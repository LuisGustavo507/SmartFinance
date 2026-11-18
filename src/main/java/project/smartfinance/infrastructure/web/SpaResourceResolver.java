package project.smartfinance.infrastructure.web;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves {@code index.html} for Angular client routes so deep links such as
 * {@code /carteiras} work on a page refresh. API, actuator, error and static
 * asset paths are left untouched: a missing one must stay a real 404.
 */
class SpaResourceResolver extends PathResourceResolver {

    private static final String INDEX = "index.html";

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        if (resourcePath.isEmpty()) {
            return index(location);
        }
        Resource resource = super.getResource(resourcePath, location);
        if (resource != null && resource.exists() && resource.isReadable()) {
            return resource;
        }
        return isClientRoute(resourcePath) ? index(location) : null;
    }

    private Resource index(Resource location) throws IOException {
        Resource index = super.getResource(INDEX, location);
        return index != null && index.exists() ? index : null;
    }

    private boolean isClientRoute(String path) {
        return !path.contains(".")
                && !isUnder(path, "api")
                && !isUnder(path, "actuator")
                && !isUnder(path, "error");
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
