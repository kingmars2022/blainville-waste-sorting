-- Full-text search over the sorting guide, for the grounded assistant.
--
-- Two parsers, because one does not fit three languages.
--
-- MySQL's default full-text parser splits on whitespace. That is right for
-- French and English and useless for Chinese, where 废电池怎么处理 is a single
-- token and matches nothing. The ngram parser fixes Chinese by tokenizing into
-- character bigrams — but applied to French and English it is far too loose:
-- measured on this dataset, "Comment réparer ma voiture ?" (a question the
-- guide cannot answer) scored 4.37 against "Encombrants" purely on incidental
-- bigram overlap, higher than some genuine matches scored. An assistant that
-- must refuse when it does not know cannot be built on that signal.
--
-- With the word parser the same nonsense query scores 0 against every row,
-- while "Where does cardboard go?" still scores 2.73 against the right one.
--
-- MATCH() picks its index by column list, so two parsers cannot share one set
-- of columns. The stored generated columns below give the ngram parser its own
-- column to index, with nothing in the application responsible for keeping
-- them in sync — the database derives them.

-- French and English: word parser.
alter table sorting_item_translation
    add fulltext index ftx_sorting_translation_word (name, instruction);

alter table sorting_item_keyword
    add fulltext index ftx_sorting_keyword_word (keyword);

-- Chinese: ngram parser, over generated copies of the same text.
alter table sorting_item_translation
    add column search_ngram text generated always as (concat(name, ' ', instruction)) stored;

alter table sorting_item_translation
    add fulltext index ftx_sorting_translation_ngram (search_ngram) with parser ngram;

alter table sorting_item_keyword
    add column keyword_ngram varchar(255) generated always as (keyword) stored;

alter table sorting_item_keyword
    add fulltext index ftx_sorting_keyword_ngram (keyword_ngram) with parser ngram;
