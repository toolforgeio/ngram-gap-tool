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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import java.io.File;
import java.io.FileInputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;
import com.google.common.io.Resources;
import com.sigpwned.tabular4j.SpreadsheetFactory;
import com.sigpwned.tabular4j.model.TabularWorksheetReader;
import io.toolforge.tool.gap.ngram.App.NgramCount;
import io.toolforge.toolforge4j.io.InputSource;
import io.toolforge.toolforge4j.io.OutputSink;

public class NgramGapTest {
  @Test
  public void computeTest() {
    List<NgramCount> ngrams = App
        .compute(Stream.of("Hello, world!", "Hello, Dolly!", "Say hello to my little friend!",
            "You had me at hello."), 1, 3)
        .sorted(Comparator.<NgramCount>naturalOrder().reversed()).toList();

    assertThat(ngrams, is(List.of(NgramCount.of("hello", 4), NgramCount.of("had me at", 1),
        NgramCount.of("hello world", 1), NgramCount.of("hello to my", 1),
        NgramCount.of("hello dolly", 1), NgramCount.of("say hello", 1),
        NgramCount.of("to my little", 1), NgramCount.of("world", 1), NgramCount.of("to my", 1),
        NgramCount.of("me at", 1), NgramCount.of("dolly", 1), NgramCount.of("you", 1),
        NgramCount.of("say", 1), NgramCount.of("had", 1), NgramCount.of("to", 1),
        NgramCount.of("my", 1), NgramCount.of("me", 1), NgramCount.of("at", 1),
        NgramCount.of("me at hello", 1), NgramCount.of("hello to", 1),
        NgramCount.of("little friend", 1), NgramCount.of("you had", 1), NgramCount.of("little", 1),
        NgramCount.of("had me", 1), NgramCount.of("friend", 1), NgramCount.of("say hello to", 1),
        NgramCount.of("my little", 1), NgramCount.of("at hello", 1), NgramCount.of("you had me", 1),
        NgramCount.of("my little friend", 1))));
  }

  @Test
  public void spreadsheetTest() throws Exception {
    File ngramGapCsv = File.createTempFile("ngramGap.", ".csv");
    ngramGapCsv.deleteOnExit();

    File ngramGapXlsx = File.createTempFile("ngramGap.", ".xlsx");
    ngramGapXlsx.deleteOnExit();

    Configuration configuration = new Configuration();
    configuration.data1 = new InputSource(Resources.getResource("data1.csv").toURI());
    configuration.textColumnName1 = "text";
    configuration.data2 = new InputSource(Resources.getResource("data2.csv").toURI());
    configuration.textColumnName2 = "text";
    configuration.minNgramLength = 1L;
    configuration.maxNgramLength = 1L;
    configuration.ngramGapCsv = new OutputSink(ngramGapCsv.toURI());
    configuration.ngramGapXlsx = new OutputSink(ngramGapXlsx.toURI());

    App.main(configuration);

    String[][] expected = new String[][] {{"hello", "2", "4", "2", "11", "4", "2.227272727272727"},
        {"world", "1", "4", "0", "11", "1", "1.1136363636363635"},
        {"dolly", "1", "4", "0", "11", "1", "1.1136363636363635"},
        {"you", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"say", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"had", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"to", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"my", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"me", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"at", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"little", "0", "4", "1", "11", "1", "1.1136363636363635"},
        {"friend", "0", "4", "1", "11", "1", "1.1136363636363635"}};

    for (File file : new File[] {ngramGapCsv, ngramGapXlsx}) {
      try (TabularWorksheetReader rows = SpreadsheetFactory.getInstance()
          .readActiveTabularWorksheet(() -> new FileInputStream(file))) {
        assertThat(rows.stream().map(row -> {
          return new String[] {
              row.findCellByColumnName("ngram").map(c -> c.getValue(String.class)).get(),
              row.findCellByColumnName("count1").map(c -> c.getValue(Integer.class)).get()
                  .toString(),
              row.findCellByColumnName("total1").map(c -> c.getValue(Integer.class)).get()
                  .toString(),
              row.findCellByColumnName("count2").map(c -> c.getValue(Integer.class)).get()
                  .toString(),
              row.findCellByColumnName("total2").map(c -> c.getValue(Integer.class)).get()
                  .toString(),
              row.findCellByColumnName("total").map(c -> c.getValue(Integer.class)).get()
                  .toString(),
              row.findCellByColumnName("chi2").map(c -> c.getValue(Double.class)).get().toString()};
        }).toArray(String[][]::new), is(expected));
      }
    }
  }
}
