package com.example.demo.loader;

import com.example.demo.model.Course;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class CourseDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CourseDataLoader.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public CourseDataLoader(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IndexOperations indexOps = elasticsearchOperations.indexOps(Course.class);

        // Always renew the index to ensure schema is up-to-date
        if (indexOps.exists()) {
            indexOps.delete();
            log.info("Deleted existing 'courses' index.");
        }

        indexOps.createWithMapping();
        log.info("Created 'courses' index with mapping from Course entity.");

        // Loading the sample data sample-course file read karne ko 
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream inputStream = new ClassPathResource("sample-courses.json").getInputStream();
        List<Course> courses = objectMapper.readValue(inputStream, new TypeReference<List<Course>>() {
        });

        elasticsearchOperations.save(courses);
        log.info("Successfully indexed {} sample courses.", courses.size());
    }
}
