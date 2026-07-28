package com.example.gameworkbench.director.tool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class DirectorToolConfiguration {
    @Bean(destroyMethod="shutdown") ExecutorService directorToolExecutor(){return Executors.newFixedThreadPool(4);}
    @Bean DirectorToolResultStore directorToolResultStore(){return new InMemoryDirectorToolResultStore();}
    @Bean DirectorToolRegistry directorToolRegistry(DirectorReadModelGateway gateway,DirectorResourceAuthorizer authorizer,
            DirectorToolResultStore store,ObjectMapper json,ExecutorService directorToolExecutor){
        return new DefaultDirectorToolRegistry(DefaultDirectorReadTools.create(gateway,json),authorizer,store,json,directorToolExecutor);
    }
}
