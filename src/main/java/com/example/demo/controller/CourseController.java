package com.example.demo.controller;

import com.example.demo.model.Course;
import com.example.demo.model.SearchResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NumberRangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
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

        // text search onn title and description with Fuzzy search
        if (q != null && !q.isBlank()) {
            boolBuilder.must(Query.of(query -> query
                    .multiMatch(m -> m
                            .fields("title", "description")
                            .query(q)
                            .fuzziness("AUTO"))));
            hasFilters = true;
        }

        // Category filter 
        if (category != null && !category.isBlank()) {
            boolBuilder.filter(Query.of(query -> query
                    .term(t -> t
                            .field("category")
                            .value(category))));
            hasFilters = true;
        }

        // Type filter 
        if (type != null && !type.isBlank()) {
            boolBuilder.filter(Query.of(query -> query
                    .term(t -> t
                            .field("type")
                            .value(type))));
            hasFilters = true;
        }

        // Price range filter
        if (minPrice != null || maxPrice != null) {
            NumberRangeQuery.Builder nrqBuilder = new NumberRangeQuery.Builder().field("price");
            if (minPrice != null) {
                nrqBuilder.gte(minPrice);
            }
            if (maxPrice != null) {
                nrqBuilder.lte(maxPrice);
            }
            boolBuilder.filter(Query.of(query -> query
                    .range(r -> r.number(nrqBuilder.build()))));
            hasFilters = true;
        }

        // age range filters
        // Find courses with greater age 
        if (minAge != null) {
            NumberRangeQuery.Builder ageBuilder = new NumberRangeQuery.Builder().field("minAge");
            ageBuilder.gte(minAge.doubleValue());
            boolBuilder.filter(Query.of(query -> query
                    .range(r -> r.number(ageBuilder.build()))));
            hasFilters = true;
        }
        // Find courses with younger age
        if (maxAge != null) {
            NumberRangeQuery.Builder ageBuilder = new NumberRangeQuery.Builder().field("maxAge");
            ageBuilder.lte(maxAge.doubleValue());
            boolBuilder.filter(Query.of(query -> query
                    .range(r -> r.number(ageBuilder.build()))));
            hasFilters = true;
        }

        // Date filter: nextSessionDate >= startDate
        if (startDate != null && !startDate.isBlank()) {
            boolBuilder.filter(Query.of(query -> query
                    .range(r -> r.date(d -> d
                            .field("nextSessionDate")
                            .gte(startDate)))));
            hasFilters = true;
        }

        // Build the query
        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        if (hasFilters) {
            queryBuilder.withQuery(Query.of(query -> query.bool(boolBuilder.build())));
        } else {
            queryBuilder.withQuery(Query.of(query -> query.matchAll(m -> m)));
        }

        // Sorting
        switch (sort) {
            case "priceAsc":
                queryBuilder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Asc)));
                break;
            case "priceDesc":
                queryBuilder.withSort(s -> s.field(f -> f.field("price").order(SortOrder.Desc)));
                break;
            case "upcoming":
            default:
                queryBuilder.withSort(s -> s.field(f -> f.field("nextSessionDate").order(SortOrder.Asc)));
                break;
        }

        // Pagination
        queryBuilder.withPageable(PageRequest.of(page, size));

        NativeQuery nativeQuery = queryBuilder.build();

        SearchHits<Course> searchHits = elasticsearchOperations.search(nativeQuery, Course.class);

        List<Course> courses = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new SearchResponse(searchHits.getTotalHits(), courses);
    }

    @GetMapping("/search/suggest")
    public List<String> suggest(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }

        NativeQuery suggestQuery = NativeQuery.builder()
                .withQuery(Query.of(query -> query
                        .match(m -> m
                                .field("suggest")
                                .query(q))))
                .withSuggester(
                        co.elastic.clients.elasticsearch.core.search.Suggester.of(s -> s
                                .suggesters("title-suggest",
                                        co.elastic.clients.elasticsearch.core.search.FieldSuggester.of(fs -> fs
                                                .prefix(q)
                                                .completion(c -> c
                                                        .field("suggest")
                                                        .size(10)
                                                        .skipDuplicates(true))))))
                .build();

        SearchHits<Course> searchHits = elasticsearchOperations.search(suggestQuery, Course.class);

        if (searchHits.getSuggest() != null && searchHits.getSuggest().getSuggestion("title-suggest") != null) {
            org.springframework.data.elasticsearch.core.suggest.response.Suggest.Suggestion<? extends org.springframework.data.elasticsearch.core.suggest.response.Suggest.Suggestion.Entry<? extends org.springframework.data.elasticsearch.core.suggest.response.Suggest.Suggestion.Entry.Option>> suggestion = searchHits
                    .getSuggest().getSuggestion("title-suggest");

            return suggestion.getEntries().stream()
                    .flatMap(entry -> entry.getOptions().stream())
                    .map(option -> option.getText())
                    .distinct()
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
