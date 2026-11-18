package project.smartfinance.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

class SpaResourceResolverTests {

    @TempDir
    Path staticRoot;

    private final SpaResourceResolver resolver = new SpaResourceResolver();

    @BeforeEach
    void createStaticFiles() throws IOException {
        Files.writeString(staticRoot.resolve("index.html"), "<app-root></app-root>");
        Files.writeString(staticRoot.resolve("main-abc123.js"), "console.log('app');");
    }

    @Test
    void clientRoutesFallBackToIndex() throws IOException {
        assertThat(resolver.getResource("carteiras", location()).getFilename())
                .isEqualTo("index.html");
        assertThat(resolver.getResource("lancamentos", location()).getFilename())
                .isEqualTo("index.html");
    }

    @Test
    void existingAssetsAreServed() throws IOException {
        Resource resource = resolver.getResource("main-abc123.js", location());

        assertThat(resource.getFilename()).isEqualTo("main-abc123.js");
    }

    @Test
    void missingAssetsStayNotFound() throws IOException {
        assertThat(resolver.getResource("assets/missing.js", location())).isNull();
    }

    @Test
    void apiActuatorAndErrorPathsNeverFallBackToTheShell() throws IOException {
        assertThat(resolver.getResource("api/nao-existe", location())).isNull();
        assertThat(resolver.getResource("actuator/desconhecido", location())).isNull();
        assertThat(resolver.getResource("error", location())).isNull();
    }

    private Resource location() {
        // ResourceHttpRequestHandler normalizes locations with a trailing separator.
        return new FileSystemResource(staticRoot + "/");
    }
}
