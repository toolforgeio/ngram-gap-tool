/*-
 * =================================LICENSE_START==================================
 * toolforge-ngram-gap-tool
 * ====================================SECTION=====================================
 * Copyright (C) 2022 - 2023 ToolForge
 * ====================================SECTION=====================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==================================LICENSE_END===================================
 */
package io.toolforge.tool.gap.ngram;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import com.sigpwned.discourse.core.util.Discourse;
import com.sigpwned.tabular4j.SpreadsheetFactory;
import com.sigpwned.tabular4j.csv.CsvSpreadsheetFormatFactory;
import com.sigpwned.tabular4j.excel.XlsxSpreadsheetFormatFactory;
import com.sigpwned.tabular4j.io.ByteSink;
import com.sigpwned.tabular4j.model.TabularWorksheetReader;
import com.sigpwned.tabular4j.model.TabularWorksheetRowWriter;
import com.sigpwned.tabular4j.model.WorksheetCellDefinition;
import com.sigpwned.uax29.Token;
import com.sigpwned.uax29.UAX29URLEmailTokenizer;
import io.toolforge.toolforge4j.io.InputSource;
import io.toolforge.toolforge4j.io.OutputSink;

public class App {
  private static final int MAX_UNIQUE_NGRAM_COUNT = 1000000;

  private static final String CSV = CsvSpreadsheetFormatFactory.DEFAULT_FILE_EXTENSION;

  private static final String XLSX = XlsxSpreadsheetFormatFactory.DEFAULT_FILE_EXTENSION;

  public static void main(String[] args) throws Exception {
    Configuration configuration = Discourse.configuration(Configuration.class, args).validate();
    if (configuration.maxNgramLength < configuration.minNgramLength)
      throw new IllegalArgumentException(
          "MaxNgramLength must be greater than or equal to MinNgramLength");
    main(configuration);
  }

  public static void main(Configuration configuration) throws Exception {
    CompletableFuture<DataNgrams> futureNgrams1 = CompletableFuture
        .supplyAsync(() -> ngrams(configuration.data1, configuration.textColumnName1,
            configuration.minNgramLength.intValue(), configuration.maxNgramLength.intValue()));
    CompletableFuture<DataNgrams> futureNgrams2 = CompletableFuture
        .supplyAsync(() -> ngrams(configuration.data2, configuration.textColumnName2,
            configuration.minNgramLength.intValue(), configuration.maxNgramLength.intValue()));

    DataNgrams ngrams1 = futureNgrams1.get();
    System.out.printf(
        "Read %d rows from Data1, which produced %d occurrences of %d unique ngrams.\n",
        ngrams1.total(), ngrams1.ngrams().stream().mapToLong(NgramCount::count).sum(),
        ngrams1.ngrams().size());

    DataNgrams ngrams2 = futureNgrams2.get();
    System.out.printf(
        "Read %d rows from Data2, which produced %d occurrences of %d unique ngrams.\n",
        ngrams2.total(), ngrams2.ngrams().stream().mapToLong(NgramCount::count).sum(),
        ngrams2.ngrams().size());

    List<NgramBucket> ngrams = merge(ngrams1.ngrams(), ngrams2.ngrams())
        .sorted(Comparator.<NgramBucket>naturalOrder().reversed()).toList();

    long total1 = ngrams.stream().mapToLong(NgramBucket::count1).sum();
    long total2 = ngrams.stream().mapToLong(NgramBucket::count2).sum();

    if (ngrams.size() > MAX_UNIQUE_NGRAM_COUNT) {
      System.out.printf("Truncating output to %d most common unique ngrams...\n",
          MAX_UNIQUE_NGRAM_COUNT);
      ngrams = ngrams.subList(0, MAX_UNIQUE_NGRAM_COUNT);
    }

    Map<String, OutputSink> outputFormats =
        Map.of(CSV, configuration.ngramGapCsv, XLSX, configuration.ngramGapXlsx);
    for (var outputFormat : outputFormats.entrySet()) {
      System.out.printf("Outputting %s report...\n", outputFormat.getKey());
      String outputFormatName = outputFormat.getKey();
      ByteSink outputFormatSink = outputFormat.getValue()::getOutputStream;
      try (TabularWorksheetRowWriter w = SpreadsheetFactory.getInstance()
          .writeTabularActiveWorksheet(outputFormatSink, outputFormatName)
          .writeHeaders("ngram", "count1", "total1", "count2", "total2", "total", "chi2")) {
        for (NgramBucket ngram : ngrams) {
          w.writeRow(List.of(WorksheetCellDefinition.ofValue(ngram.ngram()),
              WorksheetCellDefinition.ofValue(ngram.count1()),
              WorksheetCellDefinition.ofValue(total1),
              WorksheetCellDefinition.ofValue(ngram.count2()),
              WorksheetCellDefinition.ofValue(total2),
              WorksheetCellDefinition.ofValue(ngram.total()),
              WorksheetCellDefinition.ofValue(chi2(ngram, total1, total2))));
        }
      }
    }

    System.out.printf("Done!\n");
  }

  private static double chi2(NgramBucket ngram, long total1, long total2) {
    double observed = Math.max(ngram.count1(), 1.0);
    double expected =
        (Math.max(1.0, ngram.count2()) / Math.max(1.0, total2)) * Math.max(1.0, total1);
    return Math.signum(observed - expected) * Math.pow(observed - expected, 2.0) / expected;
  }

  private static record NgramBucket(String ngram, long count1, long count2)
      implements Comparable<NgramBucket> {
    public static NgramBucket of(String ngram, long count1, long count2) {
      return new NgramBucket(ngram, count1, count2);
    }

    public NgramBucket(String ngram, long count1, long count2) {
      if (count1 < 0L)
        throw new IllegalArgumentException("count1 must not be negative");
      if (count2 < 0L)
        throw new IllegalArgumentException("count2 must not be negative");
      if (count1 + count2 == 0L)
        throw new IllegalArgumentException("count1+count2 must not be greater than zero");
      this.ngram = requireNonNull(ngram);
      this.count1 = count1;
      this.count2 = count2;
    }

    public long total() {
      return count1() + count2();
    }

    private static final Comparator<NgramBucket> COMPARATOR =
        Comparator.comparingLong(NgramBucket::total).thenComparing(c -> c.ngram().hashCode());

    @Override
    public int compareTo(NgramBucket that) {
      return COMPARATOR.compare(this, that);
    }
  }

  public static Stream<NgramBucket> merge(List<NgramCount> counts1, List<NgramCount> counts2) {
    Map<String, NgramBucket> result = new HashMap<>();
    for (NgramCount count1 : counts1)
      result.put(count1.ngram(), NgramBucket.of(count1.ngram(), count1.count(), 0L));
    for (NgramCount count2 : counts2) {
      NgramBucket count1 = result.get(count2.ngram());
      if (count1 != null) {
        result.put(count2.ngram(), NgramBucket.of(count2.ngram(), count1.count1(), count2.count()));
      } else {
        result.put(count2.ngram(), NgramBucket.of(count2.ngram(), 0L, count2.count()));
      }
    }
    return result.values().stream();
  }

  public static record DataNgrams(long total, List<NgramCount> ngrams) {
    public static DataNgrams of(long rows, List<NgramCount> ngrams) {
      return new DataNgrams(rows, ngrams);
    }

    public DataNgrams(long total, List<NgramCount> ngrams) {
      if (total < 0L)
        throw new IllegalArgumentException("count must be positive");
      this.total = total;
      this.ngrams = unmodifiableList(ngrams);
    }
  }

  public static DataNgrams ngrams(InputSource data, String textColumnName, int minNgramLength,
      int maxNgramLength) {
    List<NgramCount> ngrams;

    AtomicLong total = new AtomicLong(0L);
    try (TabularWorksheetReader rows =
        SpreadsheetFactory.getInstance().readActiveTabularWorksheet(data::getInputStream)) {
      final int textColumnIndex = rows.findColumnName(textColumnName).orElseThrow(
          () -> new IllegalArgumentException("No text column with name " + textColumnName));
      System.out.printf("Reading text from column %s at index %d.\n", textColumnName,
          textColumnIndex);
      ngrams = compute(
          rows.stream().parallel().peek(r -> total.incrementAndGet())
              .map(r -> r.getCell(textColumnIndex).getValue(String.class)),
          minNgramLength, maxNgramLength).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to stream data rows", e);
    }

    return DataNgrams.of(total.longValue(), ngrams);
  }

  public static record NgramCount(String ngram, long count) implements Comparable<NgramCount> {
    public static NgramCount of(String ngram, long count) {
      return new NgramCount(ngram, count);
    }

    public NgramCount(String ngram, long count) {
      if (count < 1L)
        throw new IllegalArgumentException("count must be positive");
      this.ngram = requireNonNull(ngram);
      this.count = count;
    }

    private static final Comparator<NgramCount> COMPARATOR =
        Comparator.comparingLong(NgramCount::count).thenComparingInt(c -> c.ngram().hashCode());

    @Override
    public int compareTo(NgramCount that) {
      return COMPARATOR.compare(this, that);
    }
  }

  public static Stream<NgramCount> compute(Stream<String> texts, int minNgramLength,
      int maxNgramLength) {
    return texts.filter(Objects::nonNull).filter(not(String::isBlank))
        .flatMap(s -> ngrams(s, minNgramLength, maxNgramLength))
        .collect(groupingBy(identity(), counting())).entrySet().stream()
        .map(e -> NgramCount.of(e.getKey(), e.getValue()));
  }

  public static Stream<String> ngrams(String text, int minLength, int maxLength) {
    return ngrams(tokens(text), minLength, maxLength);
  }

  public static List<String> tokens(String text) {
    List<String> result = new ArrayList<>();
    try {
      try (UAX29URLEmailTokenizer tokenizer = new UAX29URLEmailTokenizer(text.toLowerCase())) {
        for (Token token = tokenizer.nextToken(); token != null; token =
            tokenizer.nextToken(token)) {
          result.add(token.getText());
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to tokenize text", e);
    }
    return result;
  }

  public static Stream<String> ngrams(List<String> tokens, int minLength, int maxLength) {
    return IntStream.range(0, tokens.size()).boxed().flatMap(
        i -> IntStream.rangeClosed(minLength, maxLength).filter(len -> i + len <= tokens.size())
            .mapToObj(len -> String.join(" ", tokens.subList(i, i + len))));
  }
}
