/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.api.batch.sensor.measure.internal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.sensor.SensorStorage;
import org.sonar.api.measures.CoreMetrics;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class DefaultMeasureTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void build_file_measure() {
    SensorStorage storage = mock(SensorStorage.class);
    DefaultMeasure<Integer> newMeasure = new DefaultMeasure<Integer>(storage)
      .forMetric(CoreMetrics.LINES)
      .onFile(new DefaultInputFile("foo", "src/Foo.php"))
      .withValue(3);

    assertThat(newMeasure.inputFile()).isEqualTo(new DefaultInputFile("foo", "src/Foo.php"));
    assertThat(newMeasure.metric()).isEqualTo(CoreMetrics.LINES);
    assertThat(newMeasure.value()).isEqualTo(3);

    newMeasure.save();

    verify(storage).store(newMeasure);
  }

  @Test
  public void build_project_measure() {
    SensorStorage storage = mock(SensorStorage.class);
    DefaultMeasure<Integer> newMeasure = new DefaultMeasure<Integer>(storage)
      .forMetric(CoreMetrics.LINES)
      .onProject()
      .withValue(3);

    assertThat(newMeasure.inputFile()).isNull();
    assertThat(newMeasure.metric()).isEqualTo(CoreMetrics.LINES);
    assertThat(newMeasure.value()).isEqualTo(3);

    newMeasure.save();

    verify(storage).store(newMeasure);
  }

  @Test
  public void not_allowed_to_call_onFile_and_onProject() {
    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("onProject already called");
    new DefaultMeasure<Integer>()
      .onProject()
      .onFile(new DefaultInputFile("foo", "src/Foo.php"))
      .withValue(3)
      .save();
  }

}
