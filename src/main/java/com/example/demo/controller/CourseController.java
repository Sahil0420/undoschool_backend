package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.SearchResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;

@RestController
@RequestMapping("/api")
public class CourseController {

    private final ElasticsearchOperations elasticsearchOperations;

    public CourseController(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer minAge,
            @RequestParam(required = false) Integer maxAge,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String startDate,
            @RequestParam(defaultValue = "upcoming") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        boolean hasFilters = false;

        // 1. Full text search Fuzziness 
        if (q != null && !q.isBlank()) {
            boolBuilder.must(Query.of(query -> query
                    .multiMatch(m -> m
                            .fields("title", "description")
                            .query(q)
                            .fuzziness("AUTO"))));
            hasFilters = true;
        }

        // 2. Exact Match Filters (Category/Type)
        if (category != null && !category.isBlank()) {
            boolBuilder.filter(Query.of(query -> query
                    .term(t -> t.field("category").value(category))));
            hasFilters = true;
        }

        if (type != null && !type.isBlank()) {
            boolBuilder.filter(Query.of(query -> query
                    .term(t -> t.field("type").value(type))));
            hasFilters = true;
        }

        // 3. Price Range Filter
        if (minPrice != null || maxPrice != null) {
            boolBuilder.filter(Query.of(query -> query.range(r -> {
                r.field("price");
                if (minPrice != null) r.gte(JsonData.of(minPrice));
                if (maxPrice != null) r.lte(JsonData.of(maxPrice));
                return r;
            })));
            hasFilters = true;
        }

        // 4. Age Range Filter

        //courses with greater age
        if (minAge != null) {
            boolBuilder.filter(Query.of(query -> query.range(r -> r
                    .field("minAge")
                    .gte(JsonData.of(minAge)))));
            hasFilters = true;
        }

        //courses with younger age
        if (maxAge != null) {
            boolBuilder.filter(Query.of(query -> query.range(r -> r
                    .field("maxAge")
                    .lte(JsonData.of(maxAge)))));
            hasFilters = true;
        }

        // 5. Date Filter 
        if (startDate != null && !startDate.isBlank()) {
            boolBuilder.filter(Query.of(query -> query.range(r -> r
                    .field("nextSessionDate")
                    .gte(JsonData.of(startDate)))));
            hasFilters = true;
        }

        NativeQueryBuilder queryBuilder = NativeQuery.builder();
        if (hasFilters) {
            queryBuilder.withQuery(Query.of(query -> query.bool(boolBuilder.build())));
        } else {
            queryBuilder.withQuery(Query.of(query -> query.matchAll(m -> m)));
        }

        // 6. Sorting Logic
        switch (sort) {
            case "priceAsc":
                queryBuilder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
                break;
            case "priceDesc":
                queryBuilder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
                break;
            default:
                queryBuilder.withSort(s -> s.field(f -> f.field("nextSessionDate").order(SortOrder.Asc)));
                break;
        }

        queryBuilder.withPageable(PageRequest.of(page, size));
        SearchHits<Course> searchHits = elasticsearchOperations.search(queryBuilder.build(), Course.class);

        List<Course> courses = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new SearchResponse(searchHits.getTotalHits(), courses);
    }

   @GetMapping("/search/suggest")
public List<String> suggest(@RequestParam String q) {
    if (q == null || q.isBlank()) return List.of();

    co.elastic.clients.elasticsearch.core.search.Suggester suggester = 
        co.elastic.clients.elasticsearch.core.search.Suggester.of(s -> s
            .suggesters("title-suggest", fs -> fs
                .prefix(q)
                .completion(c -> c
                    .field("suggest")
                    .size(10)
                    .skipDuplicates(true)
                )
            )
        );

    NativeQuery suggestQuery = NativeQuery.builder()
            .withSuggester(suggester) 
            .build();

    SearchHits<Course> searchHits = elasticsearchOperations.search(suggestQuery, Course.class);

    if (searchHits.getSuggest() != null) {
        var suggestion = searchHits.getSuggest().getSuggestion("title-suggest");
        if (suggestion != null) {
            return suggestion.getEntries().stream()
                    .flatMap(entry -> entry.getOptions().stream())
                    .map(option -> option.getText())
                    .distinct()
                    .collect(Collectors.toList());
        }
    }
    return List.of();
}
}