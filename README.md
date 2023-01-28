# NGRAM GAP TOOL [![tests](https://github.com/toolforgeio/ngram-gap-tool/actions/workflows/tests.yml/badge.svg)](https://github.com/toolforgeio/ngram-gap-tool/actions/workflows/tests.yml)

The NGram Gap Tool performs a comparative [frequency
analysis](https://en.wikipedia.org/wiki/Frequency_analysis) of the
[ngrams](https://en.wikipedia.org/wiki/N-gram) that make up two [text
corpora](https://en.wikipedia.org/wiki/Text_corpus) contained in one
column of two spreadsheets.

## Example Usage

For the following parameters:

    MinNgramLength: 1
    MaxNgramLength: 3

For the corpora:

    There, peeping among the cloud-wrack above a dark tower high up in
    the mountains, Sam saw a white star twinkle for a while. The
    beauty of it smote his heart, as he looked up out of the forsaken
    land, and hope returned to him. For like a shaft, clear and cold,
    the thought pierced him that in the end the Shadow was only a
    small and passing thing: there was light and high beauty for ever
    beyond its reach.
    
    Deserves it! I daresay he does. Many that live deserve death. Some
    that die deserve life. Can you give it to them? Then do not be too
    eager to deal out death in judgement. For even the very wise cannot
    see all ends. I have no much hope that Gollum can be cured before
    he dies, but there is a chance of it. And he is bound up with the
    fate of the Ring. My heart tells me that he has some part to play
    yet, for good or ill, before the end; and when that comes, the pity
    of Bilbo may rule the fate of many- yours not least.

The first 10 rows of output would be as follows:

| ngram  | count1 | total1 | count2 | total2 | total |    chi2     |
| ------ | ------ | ------ | ------ | ------ | ----- | ----------- |
| a      |   5    |  237   |   1    |  327   |   6   | 25.21844153 |
| and    |   4    |  237   |   2    |  327   |   6   | 4.487515968 |
| there  |   2    |  237   |   1    |  327   |   3   | 2.243757984 |
| up     |   2    |  237   |   1    |  327   |   3   | 2.243757984 |
| in     |   2    |  237   |   1    |  327   |   3   | 2.243757984 |
| high   |   2    |  237   |   0    |  327   |   2   | 2.243757984 |
| was    |   2    |  237   |   0    |  327   |   2   | 2.243757984 |
| him    |   2    |  237   |   0    |  327   |   2   | 2.243757984 |
| in the |   2    |  237   |   0    |  327   |   2   | 2.243757984 |
| beauty |   2    |  237   |   0    |  327   |   2   | 2.243757984 |    

The longer the text corpora are, the better the results tend to be.

## FAQ

### How are the text corpora split into tokens?

Each corpus is split into tokens according to the text segmentation
algorithm captured in
[Unicode Standard Annex #29](https://unicode.org/reports/tr29/), i.e.,
UAX29. [The specific implementation](https://github.com/sigpwned/uax29)
used includes some enhancements, namely token types for URLs, emoji,
emails, hashtags, cashtags, and mentions.

This (much) more complex approach is used instead of a (much) more
simple appproach, like splitting on whitespace, because UAX29 performs
useful text segmentation not only on romance languages, but also on
Asian languages.

UAX29 text segmentation rules operate based on whitespace and
punctuation boundaries, so users will likely find its behavior
intuitive for whitespace-delimited languages, like Romance (e.g.,
Spanish, French, Italian) and Germanic (e.g., English, German)
languages.
