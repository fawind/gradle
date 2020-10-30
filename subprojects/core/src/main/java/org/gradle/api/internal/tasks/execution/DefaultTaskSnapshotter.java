/*
 * Copyright 2018 the original author or authors.
 *
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
 */

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.FilePropertySpec;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;

public class DefaultTaskSnapshotter implements TaskSnapshotter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskSnapshotter.class);

    private final FileCollectionSnapshotter fileCollectionSnapshotter;

    public DefaultTaskSnapshotter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> snapshotTaskFiles(TaskInternal task, SortedSet<? extends FilePropertySpec> fileProperties) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (FilePropertySpec propertySpec : fileProperties) {
            LOGGER.debug("Snapshotting property {} for {}", propertySpec, task);
            List<FileSystemSnapshot> result = fileCollectionSnapshotter.snapshot(propertySpec.getPropertyFiles());

            if (propertySpec.toString().contains(".var/conf")) {
                Path path = Paths.get(propertySpec.getPropertyFiles().getAsPath());
                if (Files.isDirectory(path)) {
                    try {
                        Visitor visitor = new Visitor();
                        result.forEach(r -> r.accept(visitor));

                        List<Path> files = Files.list(path).collect(Collectors.toList());
                        LOGGER.info(">> Snapshot files for property {} for {}:\nSnapshot: {}\nDisk: {}", propertySpec, task, visitor.visited, files);
                    } catch (IOException e) {
                        throw new RuntimeException("Error listing file " + path, e);
                    }
                }
            }

            builder.put(propertySpec.getPropertyName(), CompositeFileSystemSnapshot.of(result));
        }
        return builder.build();
    }

    private static final class Visitor implements FileSystemSnapshotVisitor {

        final ArrayList<String> visited = new ArrayList<>();

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            return true;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            visited.add(fileSnapshot.getAbsolutePath());
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        }
    }
}
