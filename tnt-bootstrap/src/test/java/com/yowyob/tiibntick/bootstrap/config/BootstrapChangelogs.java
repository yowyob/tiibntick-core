package com.yowyob.tiibntick.bootstrap.config;

import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;

import java.nio.file.Path;

/**
 * Where the migration tests read {@code db/changelog/tnt-core-master.yaml} from. When failsafe runs against the Spring
 * Boot fat jar, the bootstrap's own resources sit in {@code BOOT-INF/classes}, invisible to the classloader: they are
 * read from {@code src/main/resources}, the module changelogs from their JARs. When it runs against
 * {@code target/classes} (integration-tests profile), the classloader already sees them, and adding the source
 * directory would make Liquibase find every bootstrap changelog twice ("Found 2 files with the path").
 */
public final class BootstrapChangelogs {

    private static final String MASTER = "db/changelog/tnt-core-master.yaml";

    private BootstrapChangelogs() {
    }

    public static ResourceAccessor accessor() throws Exception {
        if (BootstrapChangelogs.class.getClassLoader().getResource(MASTER) != null) {
            return new ClassLoaderResourceAccessor();
        }
        return new CompositeResourceAccessor(new DirectoryResourceAccessor(Path.of("src/main/resources")),
                new ClassLoaderResourceAccessor());
    }
}
