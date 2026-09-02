package com.example.gameworkbench.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiSurfaceCoverageTest {

    private static final String CONTROLLER_PACKAGE = "com.example.gameworkbench.controller";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyControllerEndpointIsPresentExactlyOnceInCoverageMetadata() throws Exception {
        assertExactlyCovered(discoverControllerEndpoints());
    }

    @Test
    void unclassifiedTestControllerEndpointIsRejectedByCoverageGate() throws Exception {
        Set<String> withUnclassifiedEndpoint = new LinkedHashSet<>(discoverControllerEndpoints());
        withUnclassifiedEndpoint.add("GET /api/test/unclassified-contract-fixture");

        assertThatThrownBy(() -> assertExactlyCovered(withUnclassifiedEndpoint))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("GET /api/test/unclassified-contract-fixture");
    }

    private void assertExactlyCovered(Set<String> controllerEndpoints) throws Exception {
        Path coveragePath = locate("docs/api-coverage/endpoints.json");
        assertThat(coveragePath)
                .as("versioned API coverage metadata")
                .exists();

        JsonNode root = mapper.readTree(coveragePath.toFile());
        List<String> declared = new ArrayList<>();
        root.path("endpoints").forEach(endpoint -> declared.add(key(
                endpoint.path("method").asText(), endpoint.path("path").asText())));

        assertThat(declared).doesNotHaveDuplicates();
        assertThat(new LinkedHashSet<>(declared))
                .containsExactlyInAnyOrderElementsOf(controllerEndpoints);
    }

    @Test
    void coverageMetadataConformsToVersionedSchema() throws Exception {
        Path schemaPath = locate("docs/api-coverage/schema.json");
        assertThat(schemaPath).as("coverage metadata JSON schema").exists();

        JsonNode schema = mapper.readTree(schemaPath.toFile());
        JsonNode endpointSchema = schema.path("$defs").path("endpoint");
        Set<String> required = new LinkedHashSet<>();
        endpointSchema.path("required").forEach(value -> required.add(value.asText()));
        assertThat(required).containsExactlyInAnyOrder(
                "method", "path", "domain", "lifecycle", "audience", "frontendFeature",
                "dangerLevel", "owner", "profiles", "test");

        JsonNode coverage = mapper.readTree(locate("docs/api-coverage/endpoints.json").toFile());
        Set<String> lifecycleValues = textSet(endpointSchema.path("properties").path("lifecycle").path("enum"));
        Set<String> audienceValues = textSet(endpointSchema.path("properties").path("audience").path("items").path("enum"));
        Set<String> dangerValues = textSet(endpointSchema.path("properties").path("dangerLevel").path("enum"));
        Set<String> profileValues = textSet(endpointSchema.path("properties").path("profiles").path("items").path("enum"));

        for (JsonNode endpoint : coverage.path("endpoints")) {
            assertThat(endpoint.fieldNames()).toIterable().containsAll(required);
            assertThat(lifecycleValues).contains(endpoint.path("lifecycle").asText());
            assertThat(dangerValues).contains(endpoint.path("dangerLevel").asText());
            endpoint.path("audience").forEach(value -> assertThat(audienceValues).contains(value.asText()));
            endpoint.path("profiles").forEach(value -> assertThat(profileValues).contains(value.asText()));
            assertThat(endpoint.path("owner").asText()).isNotBlank();
            String testOwner = endpoint.path("test").asText();
            assertThat(testOwner).isNotBlank();
            String[] ownerParts = testOwner.split("#", 2);
            assertThat(ownerParts).as("Class#method contract owner for " + endpoint.path("method").asText() + " " + endpoint.path("path").asText())
                    .hasSize(2);
            Class<?> testClass = Class.forName("com.example.gameworkbench.contract." + ownerParts[0]);
            java.lang.reflect.Method testMethod = testClass.getDeclaredMethod(ownerParts[1]);
            assertThat(testMethod.isAnnotationPresent(Test.class))
                    .as("executable contract test owner " + testOwner).isTrue();
            if (Set.of("internal", "deprecated", "non_prod").contains(endpoint.path("lifecycle").asText())) {
                assertThat(endpoint.path("retentionReason").asText()).isNotBlank();
                assertThat(endpoint.path("replacement").asText()).isNotBlank();
            }
        }
    }

    @Test
    void everyNonUserEndpointHasBackendMethodSecurity() throws Exception {
        JsonNode coverage = mapper.readTree(locate("docs/api-coverage/endpoints.json").toFile());
        Map<String, java.lang.reflect.Method> methods = discoverControllerMethods();
        for (JsonNode endpoint : coverage.path("endpoints")) {
            Set<String> audience = textSet(endpoint.path("audience"));
            if (audience.contains("anonymous") || audience.contains("user")) continue;
            String key = endpoint.path("method").asText() + " " + endpoint.path("path").asText();
            java.lang.reflect.Method method = methods.get(key);
            assertThat(method).as(key).isNotNull();
            PreAuthorize methodRule = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            PreAuthorize classRule = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), PreAuthorize.class);
            assertThat(methodRule != null || classRule != null)
                    .as("backend capability boundary for " + key).isTrue();
        }
    }

    private static Set<String> textSet(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    private Set<String> discoverControllerEndpoints() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Set<String> endpoints = new LinkedHashSet<>();
        for (var component : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = Class.forName(component.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            List<String> prefixes = paths(classMapping);
            for (var method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                for (String prefix : prefixes) {
                    for (String suffix : paths(mapping)) {
                        for (RequestMethod verb : methods(mapping)) {
                            endpoints.add(key(verb.name(), normalize(prefix + "/" + suffix)));
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private Map<String, java.lang.reflect.Method> discoverControllerMethods() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Map<String, java.lang.reflect.Method> endpoints = new LinkedHashMap<>();
        for (var component : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            Class<?> controller = Class.forName(component.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            for (var method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                for (String prefix : paths(classMapping)) for (String path : paths(mapping)) for (RequestMethod verb : methods(mapping)) {
                    endpoints.put(verb.name() + " " + normalize(prefix + path), method);
                }
            }
        }
        return endpoints;
    }

    private static List<String> paths(RequestMapping mapping) {
        if (mapping == null || (mapping.path().length == 0 && mapping.value().length == 0)) {
            return List.of("");
        }
        String[] values = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return Arrays.asList(values);
    }

    private static List<RequestMethod> methods(RequestMapping mapping) {
        return mapping.method().length == 0
                ? List.of(RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                        RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS,
                        RequestMethod.HEAD)
                : Arrays.asList(mapping.method());
    }

    private static String normalize(String path) {
        String normalized = path.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String key(String method, String path) {
        return method.toUpperCase() + " " + normalize(path);
    }

    private static Path locate(String workspaceRelativePath) {
        Path direct = Path.of(workspaceRelativePath);
        if (Files.exists(direct)) return direct;
        return Path.of("..").resolve(workspaceRelativePath).normalize();
    }
}
