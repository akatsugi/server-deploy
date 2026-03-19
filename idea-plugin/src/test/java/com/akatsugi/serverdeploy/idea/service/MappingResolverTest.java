package com.akatsugi.serverdeploy.idea.service;

import com.akatsugi.serverdeploy.idea.model.DirectoryMapping;
import com.akatsugi.serverdeploy.idea.model.ResolvedUploadTarget;
import com.akatsugi.serverdeploy.idea.model.ServerConfig;
import com.akatsugi.serverdeploy.idea.settings.ServerDeploySettingsState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MappingResolverTest {

    private final MappingResolver resolver = new MappingResolver();

    @Test
    void resolvesRelativeMappingAgainstServerDefaultDirectory() {
        ServerConfig server = new ServerConfig();
        server.setId("server-1");
        server.setName("dev");
        server.setDefaultDirectory("/apps/base");

        DirectoryMapping shallow = new DirectoryMapping();
        shallow.setId("mapping-1");
        shallow.setServerId("server-1");
        shallow.setLocalDirectory("D:/workspace/project");
        shallow.setRemoteDirectory("project");

        DirectoryMapping deep = new DirectoryMapping();
        deep.setId("mapping-2");
        deep.setServerId("server-1");
        deep.setLocalDirectory("D:/workspace/project/src");
        deep.setRemoteDirectory("project/source");

        List<ResolvedUploadTarget> targets = resolver.resolve(
                Path.of("D:/workspace/project/src/main/App.java"),
                List.of(server),
                List.of(shallow, deep)
        );

        assertEquals(1, targets.size());
        assertEquals("/apps/base/project/source/main/App.java", targets.get(0).getRemoteTargetPath());
    }

    @Test
    void keepsLeadingSlashMappingAsAbsolutePath() {
        ServerConfig server = new ServerConfig();
        server.setId("server-1");
        server.setName("dev");
        server.setDefaultDirectory("/data/bigdata/temp");

        DirectoryMapping mapping = new DirectoryMapping();
        mapping.setId("mapping-1");
        mapping.setServerId("server-1");
        mapping.setLocalDirectory("D:/workspace/project/target");
        mapping.setRemoteDirectory("/service-formdesign-1.1.1-SNAPSHOT");

        List<ResolvedUploadTarget> targets = resolver.resolve(
                Path.of("D:/workspace/project/target/service-formdesign-1.1.1-SNAPSHOT.jar"),
                List.of(server),
                List.of(mapping)
        );

        assertEquals(1, targets.size());
        assertEquals(
                "/service-formdesign-1.1.1-SNAPSHOT/service-formdesign-1.1.1-SNAPSHOT.jar",
                targets.get(0).getRemoteTargetPath()
        );
    }

    @Test
    void keepsAbsoluteMappingDirectoryWhenAlreadyUnderDefaultDirectory() {
        ServerConfig server = new ServerConfig();
        server.setId("server-1");
        server.setName("dev");
        server.setDefaultDirectory("/apps/base");

        DirectoryMapping mapping = new DirectoryMapping();
        mapping.setId("mapping-1");
        mapping.setServerId("server-1");
        mapping.setLocalDirectory("D:/workspace/project");
        mapping.setRemoteDirectory("/apps/base/custom/path");

        List<ResolvedUploadTarget> targets = resolver.resolve(
                Path.of("D:/workspace/project/file.txt"),
                List.of(server),
                List.of(mapping)
        );

        assertEquals(1, targets.size());
        assertEquals("/apps/base/custom/path/file.txt", targets.get(0).getRemoteTargetPath());
    }

    @Test
    void returnsNoTargetWhenSelectionDoesNotMatchAnyMapping() {
        ServerConfig server = new ServerConfig();
        server.setId("server-1");
        server.setName("dev");
        server.setDefaultDirectory("/apps/base");

        DirectoryMapping mapping = new DirectoryMapping();
        mapping.setId("mapping-1");
        mapping.setServerId("server-1");
        mapping.setLocalDirectory("D:/workspace/project");
        mapping.setRemoteDirectory("project");

        List<ResolvedUploadTarget> targets = resolver.resolve(
                Path.of("D:/another/path/file.txt"),
                List.of(server),
                List.of(mapping)
        );

        assertTrue(targets.isEmpty());
    }

    @Test
    void serializesAndDeserializesSettingsState() {
        SettingsJsonService service = new SettingsJsonService();

        ServerConfig server = new ServerConfig();
        server.setId("server-1");
        server.setName("dev");
        server.setHost("10.0.0.1");
        server.setPort(22);
        server.setUsername("root");
        server.setPassword("secret");
        server.setDefaultDirectory("/apps/demo");

        DirectoryMapping mapping = new DirectoryMapping();
        mapping.setId("mapping-1");
        mapping.setServerId("server-1");
        mapping.setLocalDirectory("D:/workspace/demo");
        mapping.setRemoteDirectory("service-demo");

        ServerDeploySettingsState state = new ServerDeploySettingsState();
        state.getServers().add(server);
        state.getMappings().add(mapping);

        String json = service.toJson(state);
        ServerDeploySettingsState restored = service.fromJson(json);

        assertEquals(1, restored.getServers().size());
        assertEquals(1, restored.getMappings().size());
        assertEquals("dev", restored.getServers().get(0).getName());
        assertEquals("service-demo", restored.getMappings().get(0).getRemoteDirectory());
    }
}