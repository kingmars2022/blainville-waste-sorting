package com.bienvenueblainville.photo;

import com.bienvenueblainville.assistant.AnswerComposer;
import com.bienvenueblainville.assistant.AssistantQuestion;
import com.bienvenueblainville.assistant.RetrievedItem;
import com.bienvenueblainville.assistant.SortingGuideRetriever;
import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.insights.QueryEventPublisher;
import com.bienvenueblainville.photo.dto.PhotoAnswer;
import com.bienvenueblainville.sorting.DestinationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The join between "what is this?" and "which bin?".
 *
 * <p>The tests are mostly about the seam rather than the model: that the bin
 * comes from retrieval, that a material the guide does not cover produces a
 * refusal rather than a guess, and that the resident is told what the model
 * thought it saw either way.
 */
class PhotoSortingServiceTest {
    private static final String PHOTO_ID = "11111111-2222-3333-4444-555555555555";

    @Mock
    private PhotoStorage storage;
    @Mock
    private PhotoIdentifier identifier;
    @Mock
    private SortingGuideRetriever retriever;
    @Mock
    private AnswerComposer composer;
    @Mock
    private QueryEventPublisher queries;

    private PhotoSortingService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(composer.providerName()).thenReturn("template");
        service = new PhotoSortingService(storage, identifier, retriever, composer, queries,
                new PhotoProperties("bucket", "ca-central-1", "", 300, 10_000_000, 0));
    }

    @Test
    void searchesTheGuideWithWhatTheModelNamedRatherThanAskingItForABin() {
        // The design in one assertion: the vision output becomes a *search
        // query*. Nothing the model returns can set the bin, because the
        // answer is composed from what retrieval found.
        when(storage.awaitProcessed(eq(PHOTO_ID), any())).thenReturn(Optional.of(new byte[]{1, 2, 3}));
        when(identifier.identify(any(), any())).thenReturn(Optional.of(new MaterialIdentification(
                "boîte à pizza", List.of("carton souillé", "boite a pizza"), false)));
        when(retriever.retrieve(any(), any())).thenReturn(List.of(item("Papiers et cartons souilles")));
        when(composer.composeWithMetadata(any(), any()))
                .thenReturn(new AnswerComposer.Composition("Bac brun.", "template"));

        PhotoAnswer answer = service.identify(PHOTO_ID, LanguageCode.fr).orElseThrow();

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(retriever).retrieve(query.capture(), any());
        // Synonyms widen the search, because a resident's word for a thing and
        // the guide's word for it are often not the same word.
        assertThat(query.getValue()).contains("boîte à pizza").contains("carton souillé");

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.identifiedAs()).isEqualTo("boîte à pizza");
        assertThat(answer.answer()).isEqualTo("Bac brun.");
        assertThat(answer.sources()).singleElement()
                .satisfies(source -> assertThat(source.binColor()).isEqualTo("brown"));
    }

    @Test
    void refusesWithoutComposingWhenTheGuideDoesNotCoverWhatWasRecognised() {
        // Recognising an object is not the same as knowing its rule. A vision
        // model that has seen a million photos of paint tins still does not
        // know what Blainville does with them.
        when(storage.awaitProcessed(eq(PHOTO_ID), any())).thenReturn(Optional.of(new byte[]{1}));
        when(identifier.identify(any(), any())).thenReturn(Optional.of(new MaterialIdentification(
                "aquarium", List.of(), false)));
        when(retriever.retrieve(any(), any())).thenReturn(List.of());

        PhotoAnswer answer = service.identify(PHOTO_ID, LanguageCode.en).orElseThrow();

        verify(composer, never()).composeWithMetadata(any(), any());
        assertThat(answer.grounded()).isFalse();
        assertThat(answer.sources()).isEmpty();
        // Naming what it saw is the difference between "retake the photo" and
        // "phone the city" - two very different next steps for the resident.
        assertThat(answer.identifiedAs()).isEqualTo("aquarium");
        assertThat(answer.answer()).contains("aquarium").contains("blainville.ca");

        // Tagged "photo", so the gap report can tell apart what residents type
        // from what they photograph - the same material is worded very
        // differently through the two.
        verify(queries).record(eq("photo"), eq(LanguageCode.en), eq("aquarium"),
                any(), eq(false), eq(0d), eq(List.of()));
    }

    @Test
    void saysItCannotSeeRatherThanGuessingWhenIdentificationFails() {
        when(storage.awaitProcessed(eq(PHOTO_ID), any())).thenReturn(Optional.of(new byte[]{1}));
        when(identifier.identify(any(), any())).thenReturn(Optional.empty());

        PhotoAnswer answer = service.identify(PHOTO_ID, LanguageCode.zh).orElseThrow();

        verify(retriever, never()).retrieve(any(), any());
        assertThat(answer.identifiedAs()).isNull();
        assertThat(answer.grounded()).isFalse();
        assertThat(answer.answer()).contains("看不清");
    }

    @Test
    void readsTheStrippedCopyAndNeverTheOriginal() {
        // The original still carries the resident's GPS coordinates. There is
        // no reason to send those to a third party either.
        when(storage.awaitProcessed(eq(PHOTO_ID), any())).thenReturn(Optional.of(new byte[]{1}));
        when(identifier.identify(any(), any())).thenReturn(Optional.empty());

        service.identify(PHOTO_ID, LanguageCode.fr);

        verify(storage).awaitProcessed(eq(PHOTO_ID), any());
        verify(storage, never()).readOriginal(any());
    }

    @Test
    void reportsNothingForAPhotoIdThatWasNeverUploaded() {
        when(storage.awaitProcessed(eq(PHOTO_ID), any())).thenReturn(Optional.empty());
        when(storage.readOriginal(PHOTO_ID)).thenReturn(Optional.empty());

        assertThat(service.identify(PHOTO_ID, LanguageCode.fr)).isEmpty();
        verify(identifier, never()).identify(any(), any());
    }

    @Test
    void saysAnUploadedPhotoIsStillBeingPreparedRatherThanThatItDoesNotExist() {
        // The Lambda runs asynchronously, so a photo can be uploaded and not
        // yet processed. Both cases used to produce the same 404 telling the
        // resident their photo did not exist - for a photo they had just
        // watched upload. They are different answers and deserve different
        // ones.
        when(storage.awaitProcessed(eq(PHOTO_ID), any())).thenReturn(Optional.empty());
        when(storage.readOriginal(PHOTO_ID)).thenReturn(Optional.of(new byte[]{1}));

        assertThatThrownBy(() -> service.identify(PHOTO_ID, LanguageCode.fr))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503")
                .hasMessageContaining("still being prepared");

        verify(identifier, never()).identify(any(), any());
    }

    @Test
    void theSearchQueryFallsBackToTheBareTermWhenThereAreNoSynonyms() {
        assertThat(new MaterialIdentification("used battery", null, false).searchQuery())
                .isEqualTo("used battery");
        assertThat(new MaterialIdentification("used battery", List.of(), false).searchQuery())
                .isEqualTo("used battery");
    }

    private static RetrievedItem item(String name) {
        return new RetrievedItem(1L, LanguageCode.fr, name, "Dans le bac brun.", null,
                DestinationType.organic, BinColor.brown, null, 8.0);
    }
}
