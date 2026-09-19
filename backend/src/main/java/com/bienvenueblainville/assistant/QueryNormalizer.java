package com.bienvenueblainville.assistant;

import com.bienvenueblainville.common.LanguageCode;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Strips function words out of a question before it reaches the full-text index.
 *
 * <p><b>Why the application does this and not MySQL.</b> InnoDB ships a
 * stopword list, and it is English-only. That gap is not theoretical: the
 * question "Quel est le numéro de téléphone du maire ?" — which the sorting
 * guide obviously cannot answer — matched the household-waste entry with a
 * score of 2.73, entirely on the word <i>est</i>, because "est" appears in that
 * entry's instruction and MySQL does not know it is a French copula. The
 * assistant answered a question about the mayor's phone number with bin
 * advice.
 *
 * <p>MySQL can be pointed at a custom stopword table, but that is a server
 * variable read at index-creation time — it would make correct behaviour
 * depend on how someone configured their MySQL, and quietly regress on a
 * machine where they hadn't. A list in the application travels with the code,
 * is unit-testable, and is visible to the next person reading it.
 *
 * <p>Chinese is left alone: it is searched through the ngram parser, which
 * does not tokenize on whitespace, so there are no word boundaries here to
 * strip on. Its false positives, if any, would need a different remedy.
 *
 * <p>The lists below are deliberately conservative — only words that cannot
 * carry meaning in a "where does this go?" question. Over-stripping would cost
 * recall, which is the more expensive mistake: a missed answer is invisible,
 * while a wrong one is not.
 */
final class QueryNormalizer {
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern DIACRITIC = Pattern.compile("\\p{M}+");

    // Short articles and prepositions are below MySQL's minimum token size, so
    // they could never match anyway - they are listed so that "is anything
    // meaningful left?" is a question this class can actually answer.
    private static final Set<String> FRENCH_SHORT = Set.of(
            "le", "la", "un", "de", "du", "au", "et", "en", "ce", "se", "je", "tu",
            "il", "on", "ma", "sa", "me", "te", "ne", "ni", "si", "va", "ai", "as",
            "a", "y", "l", "d", "c", "j", "n", "s", "t", "m");

    private static final Set<String> ENGLISH_SHORT = Set.of(
            "a", "an", "of", "to", "in", "on", "at", "it", "do", "be", "by", "or",
            "as", "if", "me", "i", "we", "he", "she", "my", "so", "up", "go");

    private static final Set<String> FRENCH = Set.of(
            "est", "sont", "etre", "une", "des", "les", "aux", "que", "qui", "quoi",
            "quel", "quelle", "quels", "quelles", "comment", "pour", "avec", "dans",
            "sur", "mon", "mes", "son", "ses", "leur", "leurs", "cette", "cet", "ces",
            "par", "pas", "plus", "mais", "donc", "ou", "dois", "puis", "peut", "peux",
            "vous", "nous", "elle", "ils", "elles", "cela", "chez", "vers", "sous",
            "entre", "alors", "tout", "tous", "toute", "toutes", "quand", "aussi",
            // Verb forms carrying no sorting meaning. "vont" earned its place
            // here: "Où vont les branches coupées ?" ranked the green-waste
            // entry above the dedicated Branches entry, because "vont" appears
            // in the green-waste instruction and tipped a near-tie.
            "vont", "vais", "aller", "faire", "fait", "faut", "doit", "doivent",
            "peuvent", "veux", "veut");

    private static final Set<String> ENGLISH = Set.of(
            "the", "is", "are", "was", "were", "does", "did", "what", "where", "when",
            "how", "why", "which", "who", "can", "could", "should", "would", "will",
            "shall", "this", "that", "these", "those", "and", "but", "for", "with",
            "from", "into", "about", "you", "your", "our", "their", "there", "here",
            "some", "any", "have", "has", "had", "been", "they", "them", "then");

    private QueryNormalizer() {
    }

    /**
     * @return the question with function words removed, or an empty string if
     *         nothing meaningful is left — which the retriever treats as "no
     *         results", i.e. a refusal
     */
    static String normalize(String question, LanguageCode language) {
        Set<String> stopwords = switch (language) {
            case fr -> union(FRENCH, FRENCH_SHORT);
            case en -> union(ENGLISH, ENGLISH_SHORT);
            case zh -> Set.of();
        };

        if (stopwords.isEmpty()) {
            return question.trim();
        }

        return Arrays.stream(NOT_WORD.split(question.trim()))
                .filter(token -> !token.isEmpty())
                .filter(token -> !stopwords.contains(fold(token)))
                .collect(Collectors.joining(" "));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        return Stream.concat(a.stream(), b.stream()).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Lowercase and strip accents, so "Où" matches the stopword "ou". The
     * folded form is only ever used for the lookup; the original token is what
     * goes to MySQL, whose collation does its own accent handling.
     */
    private static String fold(String token) {
        String decomposed = Normalizer.normalize(token.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return DIACRITIC.matcher(decomposed).replaceAll("");
    }
}
