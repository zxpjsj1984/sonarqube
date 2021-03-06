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
package org.sonar.batch.report;

import org.apache.commons.io.FileUtils;
import org.sonar.api.batch.bootstrap.ProjectReactor;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.batch.index.BatchResource;
import org.sonar.batch.index.ResourceCache;
import org.sonar.batch.protocol.output.resource.ReportComponent;
import org.sonar.batch.protocol.output.resource.ReportComponents;

import java.io.File;
import java.io.IOException;

public class ComponentsPublisher implements ReportPublisher {

  private final ResourceCache resourceCache;
  private final ProjectReactor reactor;

  public ComponentsPublisher(ProjectReactor reactor, ResourceCache resourceCache) {
    this.reactor = reactor;
    this.resourceCache = resourceCache;
  }

  @Override
  public void export(File reportDir) throws IOException {
    ReportComponents components = new ReportComponents();
    BatchResource rootProject = resourceCache.get(reactor.getRoot().getKeyWithBranch());
    components.setRoot(buildResourceForReport(rootProject));
    File resourcesFile = new File(reportDir, "components.json");
    FileUtils.write(resourcesFile, components.toJson());
  }

  private ReportComponent buildResourceForReport(BatchResource batchResource) {
    Resource r = batchResource.resource();
    ReportComponent result = new ReportComponent()
      .setBatchId(batchResource.batchId())
      .setSnapshotId(batchResource.snapshotId())
      .setId(r.getId())
      .setName(r.getName())
      .setPath(r.getPath())
      .setType(getType(r));
    for (BatchResource child : batchResource.children()) {
      result.addChild(buildResourceForReport(child));
    }
    return result;
  }

  private ReportComponent.Type getType(Resource r) {
    if (ResourceUtils.isFile(r)) {
      return ReportComponent.Type.FIL;
    } else if (ResourceUtils.isDirectory(r)) {
      return ReportComponent.Type.DIR;
    } else if (ResourceUtils.isModuleProject(r)) {
      return ReportComponent.Type.MOD;
    } else if (ResourceUtils.isRootProject(r)) {
      return ReportComponent.Type.PRJ;
    }
    throw new IllegalArgumentException("Unknow resource type: " + r);
  }

}
