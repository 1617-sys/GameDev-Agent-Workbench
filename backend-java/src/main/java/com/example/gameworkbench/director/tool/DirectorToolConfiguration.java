package com.example.gameworkbench.director.tool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import com.example.gameworkbench.experiment.PlayerExperimentService;
import com.example.gameworkbench.experiment.candidate.DeterministicCandidateGenerator;
import com.example.gameworkbench.prototype.PrototypeDraftService;

@Configuration
public class DirectorToolConfiguration {
    @Bean(destroyMethod="shutdown") ExecutorService directorToolExecutor(){return Executors.newFixedThreadPool(4);}
    @Bean DirectorToolResultStore directorToolResultStore(){return new InMemoryDirectorToolResultStore();}
    @Bean DirectorToolRegistry directorToolRegistry(DirectorReadModelGateway gateway,DirectorResourceAuthorizer authorizer,
            DirectorToolResultStore store,ObjectMapper json,ExecutorService directorToolExecutor,PrototypeDraftService drafts,
            DeterministicCandidateGenerator generator,PlayerExperimentService experiments){
        var tools=new ArrayList<>(DefaultDirectorReadTools.create(gateway,json));tools.addAll(ExperimentDirectorTools.create(drafts,generator,experiments,json));
        return new DefaultDirectorToolRegistry(tools,authorizer,store,json,directorToolExecutor);
    }
}
