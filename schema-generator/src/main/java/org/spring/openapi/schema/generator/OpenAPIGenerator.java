package org.spring.openapi.schema.generator;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.openapi.schema.generator.annotations.OpenApiIgnore;
import org.spring.openapi.schema.generator.model.GenerationContext;
import org.spring.openapi.schema.generator.model.InheritanceInfo;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

public class OpenAPIGenerator {

    private static Logger logger = LoggerFactory.getLogger(OpenAPIGenerator.class);

    private static final String DEFAULT_DISCRIMINATOR_NAME = "type";

    private List<String> modelPackages;
    private List<String> controllerBasePackages;

    private ComponentSchemaTransformer componentSchemaTransformer;

    public OpenAPIGenerator(List<String> modelPackages, List<String> controllerBasePackages) {
        this.modelPackages = modelPackages;
        this.controllerBasePackages = controllerBasePackages;
        componentSchemaTransformer = new ComponentSchemaTransformer();
    }

    public OpenAPI generate() {
        logger.info("Starting OpenAPI generation");
        OpenAPI openAPI = new OpenAPI();
        openAPI.setComponents(createComponentsWrapper());
        logger.info("OpenAPI generation done!");
        return openAPI;
    }

    private Components createComponentsWrapper() {
        Components componentsWrapper = new Components();
        componentsWrapper.setSchemas(createSchemas());
        return componentsWrapper;
    }

    private Map<String, Schema> createSchemas() {
        Map<String, Schema> schemaMap = new HashMap<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        modelPackages.forEach(modelPackage -> scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(modelPackage))));

        List<String> packagesWithoutRegex = removeRegexFormatFromPackages(modelPackages);
        for (String modelPackage : packagesWithoutRegex) {
            logger.info("Scanning package=[{}]", modelPackage);
            Map<String, InheritanceInfo> inheritanceMap = new HashMap<>();
            for (BeanDefinition beanDefinition : scanner.findCandidateComponents(modelPackage)) {
                logger.info("Scanning class=[{}]", beanDefinition.getBeanClassName());
                // populating inheritance info
                Class<?> clazz = getClass(beanDefinition);
                if (inheritanceMap.containsKey(clazz.getName()) || AnnotationUtils.getAnnotation(clazz, OpenApiIgnore.class) != null) {
                    continue;
                }
                getInheritanceInfo(clazz).ifPresent(info -> {
                    logger.info("Adding entry [{}] to inheritance map", clazz.getName());
                    inheritanceMap.put(clazz.getName(), info);
                });
            }
            for (BeanDefinition beanDefinition : scanner.findCandidateComponents(modelPackage)) {
                Class<?> clazz = getClass(beanDefinition);
                if (schemaMap.containsKey(clazz.getSimpleName()) || AnnotationUtils.getAnnotation(clazz, OpenApiIgnore.class) != null) {
                    continue;
                }
                GenerationContext generationContext = new GenerationContext(inheritanceMap, packagesWithoutRegex);
                schemaMap.put(clazz.getSimpleName(), componentSchemaTransformer.transformSimpleSchema(clazz, generationContext));
            }

        }
        return schemaMap;
    }

    private Class<?> getClass(BeanDefinition beanDefinition) {
        try {
            return Class.forName(beanDefinition.getBeanClassName());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private Optional<InheritanceInfo> getInheritanceInfo(Class<?> clazz) {
        if (AnnotationUtils.getAnnotation(clazz, JsonSubTypes.class) != null) {
            List<Annotation> annotations = unmodifiableList(asList(clazz.getAnnotations()));
            JsonTypeInfo jsonTypeInfo = annotations.stream()
                    .filter(annotation -> annotation instanceof JsonTypeInfo)
                    .map(annotation -> (JsonTypeInfo) annotation)
                    .findFirst()
                    .orElse(null);

            InheritanceInfo inheritanceInfo = new InheritanceInfo();
            inheritanceInfo.setDiscriminatorFieldName(getDiscriminatorName(jsonTypeInfo));
            inheritanceInfo.setDiscriminatorClassMap(scanJacksonInheritance(annotations));
            return Optional.of(inheritanceInfo);
        }
        return Optional.empty();
    }

    private String getDiscriminatorName(JsonTypeInfo jsonTypeInfo) {
        if (jsonTypeInfo == null) {
            return DEFAULT_DISCRIMINATOR_NAME;
        }
        return jsonTypeInfo.property();
    }

    private List<String> removeRegexFormatFromPackages(List<String> modelPackages) {
        return modelPackages.stream()
                .map(modelPackage -> modelPackage.replace(".*", ""))
                .collect(Collectors.toList());
    }

    private Map<String, String> scanJacksonInheritance(List<Annotation> annotations) {
        return annotations.stream()
                .filter(annotation -> annotation instanceof JsonSubTypes)
                .map(annotation -> (JsonSubTypes) annotation)
                .flatMap(jsonSubTypesMapped -> Arrays.stream(jsonSubTypesMapped.value()))
                .collect(Collectors.toMap(o -> o.value().getSimpleName(), JsonSubTypes.Type::name));
    }

}