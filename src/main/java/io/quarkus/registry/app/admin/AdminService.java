package io.quarkus.registry.app.admin;

import javax.enterprise.context.ApplicationScoped;
import javax.transaction.Transactional;

import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.app.CacheNames;
import io.quarkus.registry.app.events.ExtensionCatalogImportEvent;
import io.quarkus.registry.app.events.ExtensionCompatibilityCreateEvent;
import io.quarkus.registry.app.events.ExtensionCompatibleDeleteEvent;
import io.quarkus.registry.app.events.ExtensionCreateEvent;
import io.quarkus.registry.app.model.Extension;
import io.quarkus.registry.app.model.ExtensionRelease;
import io.quarkus.registry.app.model.ExtensionReleaseCompatibility;
import io.quarkus.registry.app.model.Platform;
import io.quarkus.registry.app.model.PlatformExtension;
import io.quarkus.registry.app.model.PlatformRelease;
import io.quarkus.registry.catalog.ExtensionCatalog;
import org.jboss.logging.Logger;

/**
 * This class listens for async events fired from {@link AdminResource}
 */
@ApplicationScoped
public class AdminService {

    private static final Logger logger = Logger.getLogger(AdminService.class);

    @Transactional
    @CacheInvalidateAll(cacheName = CacheNames.METADATA)
    public void onExtensionCatalogImport(ExtensionCatalogImportEvent event) {
        try {
            ExtensionCatalog extensionCatalog = event.getExtensionCatalog();
            PlatformRelease platformRelease = insertPlatform(event.getPlatform(), extensionCatalog);
            for (io.quarkus.registry.catalog.Extension extension : extensionCatalog.getExtensions()) {
                insertExtensionRelease(extension, platformRelease);
            }
        } catch (Exception e) {
            logger.error("Error while inserting platform", e);
            throw new IllegalStateException(e);
        }
    }

    private PlatformRelease insertPlatform(Platform platform, ExtensionCatalog extensionCatalog) {
        return null;
    }

    @Transactional
    @CacheInvalidateAll(cacheName = CacheNames.METADATA)
    public void onExtensionCreate(ExtensionCreateEvent event) {
        // Non-platform extension
        try {
            insertExtensionRelease(event.getExtension(), null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e);
        }
    }

    private ExtensionRelease insertExtensionRelease(io.quarkus.registry.catalog.Extension ext,
                                                    PlatformRelease platformRelease) {
        ArtifactCoords coords = ext.getArtifact();
        final String groupId = coords.getGroupId();
        final String artifactId = coords.getArtifactId();
        final String version = coords.getVersion();
        final Extension extension = Extension.findByGA(groupId, artifactId)
                .orElseGet(() -> {
                    Extension newExtension = new Extension();
                    newExtension.groupId = coords.getGroupId();
                    newExtension.artifactId = artifactId;
                    newExtension.name = ext.getName();
                    newExtension.description = ext.getDescription();

                    newExtension.persist();
                    return newExtension;
                });
        return ExtensionRelease.findByGAV(groupId, artifactId, version)
                .orElseGet(() -> {
                    ExtensionRelease newExtensionRelease = new ExtensionRelease();
                    newExtensionRelease.version = version;
                    newExtensionRelease.extension = extension;
                    String quarkusCore = (String) ext.getMetadata().get(io.quarkus.registry.catalog.Extension.MD_BUILT_WITH_QUARKUS_CORE);
                    // Some extensions were published using the full GAV
                    if (quarkusCore != null && quarkusCore.contains(":")) {
                        try {
                            quarkusCore = ArtifactCoords.fromString(quarkusCore).getVersion();
                        } catch (IllegalArgumentException iae) {
                            // ignore
                        }
                    } else {
                        // Cannot determine Quarkus version
                        quarkusCore = "0.0.0";
                    }
                    newExtensionRelease.quarkusCoreVersion = quarkusCore;
                    // Many-to-many
                    if (platformRelease != null) {
                        PlatformExtension platformExtension = new PlatformExtension();
                        platformExtension.extensionRelease = newExtensionRelease;
                        platformExtension.platformRelease = platformRelease;
                        platformExtension.metadata = ext.getMetadata();

                        platformRelease.extensions.add(platformExtension);
                        newExtensionRelease.platforms.add(platformExtension);
                    } else {
                        newExtensionRelease.metadata = ext.getMetadata();
                    }
                    newExtensionRelease.persist();
                    return newExtensionRelease;
                });
    }

    @Transactional
    @CacheInvalidateAll(cacheName = CacheNames.METADATA)
    public void onExtensionCompatibilityCreate(ExtensionCompatibilityCreateEvent event) {
        ExtensionReleaseCompatibility extensionReleaseCompatibility = ExtensionReleaseCompatibility.findByNaturalKey(event.getExtensionRelease(), event.getQuarkusCore()).orElseGet(() -> {
            ExtensionReleaseCompatibility newEntity = new ExtensionReleaseCompatibility();
            newEntity.extensionRelease = event.getExtensionRelease();
            newEntity.quarkusCoreVersion = event.getQuarkusCore();
            return newEntity;
        });
        extensionReleaseCompatibility.compatible = event.isCompatible();
        extensionReleaseCompatibility.persistAndFlush();
    }

    @Transactional
    @CacheInvalidateAll(cacheName = CacheNames.METADATA)
    public void onExtensionCompatibilityDelete(ExtensionCompatibleDeleteEvent event) {
        ExtensionReleaseCompatibility.delete("from ExtensionReleaseCompatible rc where rc.extensionRelease = ?1 and rc.quarkusCore = ?2",
                                             event.getExtensionRelease(),
                                             event.getQuarkusCore());
    }
}