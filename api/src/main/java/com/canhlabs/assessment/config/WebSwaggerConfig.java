package com.canhlabs.assessment.config;

import com.canhlabs.assessment.share.AppConstant;
import com.google.common.collect.Sets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger.web.OperationsSorter;
import springfox.documentation.swagger.web.UiConfiguration;
import springfox.documentation.swagger.web.UiConfigurationBuilder;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * 
 * @author CaNguyen
 *
 */
@Configuration
@EnableSwagger2
public class WebSwaggerConfig {

    private static final Set<String> DEFAULT_PRODUCES_AND_CONSUMES = Set.of(
            "application/json"
    );
    
    @Bean
    public Docket apiInternal() {
        return new Docket(DocumentationType.SWAGGER_2)
                .produces(DEFAULT_PRODUCES_AND_CONSUMES)
                .consumes(DEFAULT_PRODUCES_AND_CONSUMES)
                .groupName("1-internal")
                .tags(new Tag(AppConstant.API.TAG_API, "REST API for Assessment"), addDefineTag())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.canhlabs.assessment.web"))
                .build()
                .apiInfo(apiEndPointsInfo())
                
                .securitySchemes(Collections.singletonList(apiKey()))
                .protocols(Sets.newHashSet("http", "https"))
                .securityContexts(Collections.singletonList(securityContext()));
    }

    @Bean
    public UiConfiguration uiConfig() {
        return UiConfigurationBuilder
                .builder()
                .operationsSorter(OperationsSorter.METHOD)
                .build();
    }

    private ApiInfo apiEndPointsInfo() {
        return new ApiInfoBuilder()
                .title("API - Operation Dashboard")
                //.description(description)
                .contact(new Contact("ca", "www.canhlabs.com", "noemail@email.com "))
                //.license("Apache 2.0")
               // .extensions(addExtendsion())
                //.licenseUrl("http://www.apache.org/licenses/LICENSE-2.0.html")
                .version("1.0.0")

                .build();
    }

    private SecurityContext securityContext() {
        return SecurityContext.builder().securityReferences(defaultAuth()).build();
    }

    private List<SecurityReference> defaultAuth() {
        final AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        final AuthorizationScope[] authorizationScopes = new AuthorizationScope[] { authorizationScope };
        return Collections.singletonList(new SecurityReference("Bearer", authorizationScopes));
    }

    private ApiKey apiKey() {
        return new ApiKey("Bearer", "Authorization", "header");
    }

    private Tag[] addDefineTag() {
        // create new tag when having new API
        return new Tag[0];
    }
}
