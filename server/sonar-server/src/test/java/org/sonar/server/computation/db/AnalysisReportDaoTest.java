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

package org.sonar.server.computation.db;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.TempFolder;
import org.sonar.core.computation.db.AnalysisReportDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.DbTester;
import org.sonar.core.persistence.MyBatis;
import org.sonar.test.DbTests;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.core.computation.db.AnalysisReportDto.Status.PENDING;
import static org.sonar.core.computation.db.AnalysisReportDto.Status.WORKING;

@Category(DbTests.class)
public class AnalysisReportDaoTest {
  private static final String DEFAULT_PROJECT_KEY = "123456789-987654321";
  private static final long DEFAULT_SNAPSHOT_ID = 123L;

  @Rule
  public DbTester db = new DbTester();
  private AnalysisReportDao sut;
  private DbSession session;
  private System2 system2;
  private TempFolder tempFolder;

  @Before
  public void before() {
    this.session = db.myBatis().openSession(false);
    this.system2 = mock(System2.class);
    this.tempFolder = mock(TempFolder.class);
    this.sut = new AnalysisReportDao(system2, tempFolder);

    when(system2.now()).thenReturn(DateUtils.parseDate("2014-09-26").getTime());
  }

  @After
  public void after() {
    MyBatis.closeQuietly(session);
  }

  @Test
  public void insert_multiple_reports() throws Exception {
    db.prepareDbUnit(getClass(), "empty.xml");

    AnalysisReportDto report1 = newDefaultAnalysisReport();
    AnalysisReportDto report2 = newDefaultAnalysisReport();

    sut.insert(session, report1);
    sut.insert(session, report2);
    session.commit();

    db.assertDbUnit(getClass(), "insert-result.xml", "analysis_reports");
  }

  @Test
  public void insert_report_data() throws Exception {
    db.prepareDbUnit(getClass(), "empty.xml");
    AnalysisReportDto report = newDefaultAnalysisReport()
      .setData(IOUtils.toInputStream("default-project"));

    sut.insert(session, report);

    assertThatReportDataIsEqualTo("default-project");
  }

  private void assertThatReportDataIsEqualTo(String reportData) throws SQLException, IOException {
    PreparedStatement ps = session.getConnection().prepareStatement("select report_data from analysis_reports");
    ResultSet rs = ps.executeQuery();
    rs.next();
    InputStream reportDataStream = rs.getBinaryStream(1);
    assertThat(IOUtils.toString(reportDataStream)).isEqualTo(reportData);
  }

  @Test
  public void update_all_to_status() {
    db.prepareDbUnit(getClass(), "update-all-to-status-pending.xml");

    sut.cleanWithUpdateAllToPendingStatus(session);
    session.commit();

    db.assertDbUnit(getClass(), "update-all-to-status-pending-result.xml", "analysis_reports");
  }

  @Test
  public void truncate() {
    db.prepareDbUnit(getClass(), "any-analysis-reports.xml");

    sut.cleanWithTruncate(session);
    session.commit();

    db.assertDbUnit(getClass(), "truncate-result.xml", "analysis_reports");
  }

  @Test
  public void find_one_report_by_project_key() {
    db.prepareDbUnit(getClass(), "select.xml");

    final String projectKey = "123456789-987654321";
    List<AnalysisReportDto> reports = sut.findByProjectKey(session, projectKey);
    AnalysisReportDto report = reports.get(0);

    assertThat(reports).hasSize(1);
    assertThat(report.getProjectKey()).isEqualTo(projectKey);
    assertThat(report.getId()).isEqualTo(1);
  }

  @Test
  public void find_several_reports_by_project_key() {
    db.prepareDbUnit(getClass(), "select.xml");

    final String projectKey = "987654321-123456789";
    List<AnalysisReportDto> reports = sut.findByProjectKey(session, projectKey);

    assertThat(reports).hasSize(2);
  }

  @Test
  public void get_oldest_available_report() {
    db.prepareDbUnit(getClass(), "select_oldest_available_report.xml");

    final String projectKey = "123456789-987654321";
    AnalysisReportDto nextAvailableReport = sut.getNextAvailableReport(session);

    assertThat(nextAvailableReport.getId()).isEqualTo(2);
    assertThat(nextAvailableReport.getProjectKey()).isEqualTo(projectKey);
  }

  @Test
  public void get_oldest_available_report_with_working_reports_older() {
    db.prepareDbUnit(getClass(), "select_oldest_available_report_with_working_reports_older.xml");

    final String projectKey = "123456789-987654321";
    AnalysisReportDto nextAvailableReport = sut.getNextAvailableReport(session);

    assertThat(nextAvailableReport.getId()).isEqualTo(2);
    assertThat(nextAvailableReport.getProjectKey()).isEqualTo(projectKey);
  }

  @Test
  public void null_when_no_available_pending_report_because_working_report_on_the_same_project() {
    db.prepareDbUnit(getClass(), "select-with-no-available-report.xml");

    AnalysisReportDto nextAvailableReport = sut.getNextAvailableReport(session);

    assertThat(nextAvailableReport).isNull();
  }

  @Test
  public void getById_maps_all_the_fields_except_the_data() {
    db.prepareDbUnit(getClass(), "one_analysis_report.xml");

    AnalysisReportDto report = sut.getById(session, 1L);

    assertThat(report.getProjectKey()).isEqualTo(DEFAULT_PROJECT_KEY);
    assertThat(report.getCreatedAt()).isEqualTo(DateUtils.parseDate("2014-09-24"));
    assertThat(report.getUpdatedAt()).isEqualTo(DateUtils.parseDate("2014-09-25"));
    assertThat(report.getStartedAt()).isEqualTo(DateUtils.parseDate("2014-09-26"));
    assertThat(report.getFinishedAt()).isEqualTo(DateUtils.parseDate("2014-09-27"));
    assertThat(report.getStatus()).isEqualTo(WORKING);
    assertThat(report.getData()).isNull();
    assertThat(report.getKey()).isEqualTo("1");
  }

  @Test
  public void getById_returns_null_when_id_not_found() {
    db.prepareDbUnit(getClass(), "select.xml");

    AnalysisReportDto report = sut.getById(session, 4L);

    assertThat(report).isNull();
  }

  @Test(expected = NullPointerException.class)
  public void nullPointerExc_when_trying_to_book_a_report_without_id() {
    sut.bookAnalysisReport(session, new AnalysisReportDto());
  }

  @Test
  public void cannot_book_an_already_working_report_analysis() {
    db.prepareDbUnit(getClass(), "one_busy_report_analysis.xml");

    AnalysisReportDto report = newDefaultAnalysisReport();
    AnalysisReportDto reportBooked = sut.bookAnalysisReport(session, report);

    assertThat(reportBooked).isNull();
  }

  @Test
  public void book_one_available_report_analysis() {
    Date mockedNow = DateUtils.parseDate("2014-09-30");
    when(system2.now()).thenReturn(mockedNow.getTime());
    db.prepareDbUnit(getClass(), "one_available_analysis.xml");

    AnalysisReportDto report = newDefaultAnalysisReport();
    AnalysisReportDto reportBooked = sut.bookAnalysisReport(session, report);

    assertThat(reportBooked.getId()).isEqualTo(1L);
    assertThat(reportBooked.getStatus()).isEqualTo(WORKING);
    assertThat(reportBooked.getStartedAt()).isEqualTo(mockedNow);
  }

  @Test
  public void can_book_report_while_another_one_working_on_the_same_project() {
    db.prepareDbUnit(getClass(), "one_available_analysis_but_another_busy_on_same_project.xml");

    AnalysisReportDto report = newDefaultAnalysisReport();
    AnalysisReportDto reportBooked = sut.bookAnalysisReport(session, report);

    assertThat(reportBooked).isNotNull();
  }

  @Test
  public void book_available_report_analysis_while_having_one_working_on_another_project() {
    db.prepareDbUnit(getClass(), "book_available_report_analysis_while_having_one_working_on_another_project.xml");

    AnalysisReportDto report = newDefaultAnalysisReport();
    AnalysisReportDto reportBooked = sut.bookAnalysisReport(session, report);

    assertThat(reportBooked.getId()).isEqualTo(1L);
  }

  @Test
  public void delete_one_analysis_report() {
    db.prepareDbUnit(getClass(), "one_analysis_report.xml");

    sut.delete(session, newDefaultAnalysisReport());
    session.commit();

    db.assertDbUnit(getClass(), "truncate-result.xml", "analysis_reports");
  }

  @Test
  public void findAll_one_analysis_report() {
    db.prepareDbUnit(getClass(), "one_analysis_report.xml");

    List<AnalysisReportDto> reports = sut.findAll(session);

    assertThat(reports).hasSize(1);
  }

  @Test
  public void findAll_empty_table() {
    db.prepareDbUnit(getClass(), "empty.xml");

    List<AnalysisReportDto> reports = sut.findAll(session);

    assertThat(reports).isEmpty();
  }

  @Test
  public void findAll_three_analysis_reports() {
    db.prepareDbUnit(getClass(), "three_analysis_reports.xml");

    List<AnalysisReportDto> reports = sut.findAll(session);

    assertThat(reports).hasSize(3);
  }

  @Test
  public void insert_and_then_retrieve_report_data_with_decompressed_files_medium_test() throws Exception {
    // ARRANGE
    db.prepareDbUnit(getClass(), "empty.xml");
    AnalysisReportDto report = newDefaultAnalysisReport();
    InputStream zip = getClass().getResource("/org/sonar/server/computation/db/AnalysisReportDaoTest/zip.zip").openStream();
    report.setData(zip);

    File toDir = new File("target/tmp/unzip-report-in-database/");
    when(tempFolder.newDir()).thenReturn(toDir);
    sut.insert(session, report);
    session.commit();
    IOUtils.closeQuietly(zip);

    // ACT
    sut.getDecompressedReport(session, 1L);

    // ASSERT
    assertThat(toDir.list()).hasSize(3);
  }

  @Test(expected = UnsupportedOperationException.class)
  public void doGetNullableByKey_is_not_implemented_yet() {
    sut.doGetNullableByKey(session, "ANY_STRING");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void getSynchronizationParams_is_not_implemented_yet() {
    sut.getSynchronizationParams(new Date(), new HashMap<String, String>());
  }

  @Test(expected = UnsupportedOperationException.class)
  public void doUpdate_is_not_implemented_yet() {
    sut.doUpdate(session, new AnalysisReportDto());
  }

  private AnalysisReportDto newDefaultAnalysisReport() {
    return AnalysisReportDto.newForTests(1L)
      .setProjectKey(DEFAULT_PROJECT_KEY)
      .setSnapshotId(DEFAULT_SNAPSHOT_ID)
      .setData(null)
      .setStatus(PENDING)
      .setStartedAt(DateUtils.parseDate("2014-09-25"))
      .setFinishedAt(DateUtils.parseDate("2014-09-27"))
      .setCreatedAt(DateUtils.parseDate("2014-09-24"))
      .setUpdatedAt(DateUtils.parseDate("2014-09-25"));
  }
}
