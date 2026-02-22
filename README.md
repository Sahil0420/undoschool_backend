# UndoSchool Backend - Elasticsearch Search API

This is a Spring Boot application that provides a robust Search API over an Elasticsearch index containing 50 diverse courses. The project satisfies all requirements for **Assignment A (Basic Course Search with Filters)** and **Assignment B (Bonus - Autocomplete Suggestions & Fuzzy Search)**.

## Prerequisites

- **Java 17**
- **Maven**
- **Docker & Docker Compose**

---

## 1. Launch Elasticsearch

The repository contains a `docker-compose.yml` file pre-configured for a single-node Elasticsearch cluster (v9.0.2).

To start the cluster, run the following command in the project root:

```bash
docker-compose up -d
```

Verify that Elasticsearch is running successfully:

```bash
curl -s http://localhost:9200
```

*Expected Response*: You should see a JSON block returning the cluster name, version, and the tagline `"You Know, for Search"`.

---

## 2. Build and Run the Spring Boot Application

Before starting the application, ensure Elasticsearch is fully up and running on `localhost:9200`.

To compile the application, run:

```bash
mvn clean compile
```

To start the Spring Boot server, run:

```bash
mvn spring-boot:run
```

The application will launch on `http://localhost:8080`.

---

## 3. Populating the Index with Sample Data

**Data is populated automatically at startup.**

The application includes a `CourseDataLoader` component (`ApplicationRunner`). Every time the application starts, it performs the following steps:
1. Deletes the existing `courses` index (if any).
2. Creates a fresh `courses` index with the correct mappings defined in the `Course` entity (including the specialized `suggest` field for autocomplete).
3. Reads `src/main/resources/sample-courses.json`.
4. Bulk-indexes all 50 sample courses into Elasticsearch.

Check your console logs during startup; you should see:
```text
Deleted existing 'courses' index.
Created 'courses' index with mapping from Course entity.
Successfully indexed 50 sample courses.
```
---

## 4. API Usage (Assignment A: Basic Search & Filters)

The main search endpoint is `GET /api/search`. Below are various `curl` examples demonstrating how to use different filters alone and in combination.

### 4.1. Get All Courses (Default Pagination)
Retrieves the first 10 courses, sorted by upcoming `nextSessionDate`.
```bash
curl -s "http://localhost:8080/api/search"
```

### 4.2. Full-Text Search
Searches against both `title` and `description` fields.
```bash
curl -s "http://localhost:8080/api/search?q=python"
```

### 4.3. Exact Match Filters (Category & Type)
Filters by exact keywords (`category` and `type`). Valid `type` values are `ONE_TIME`, `COURSE`, or `CLUB`.
```bash
curl -s "http://localhost:8080/api/search?category=Art&type=CLUB"
```

### 4.4. Range Filters (Price & Age)
Filters numerical ranges for price, and ensures the target course min/max age overlaps appropriately.
```bash
# Courses between $20 and $50
curl -s "http://localhost:8080/api/search?minPrice=20&maxPrice=50"

# Courses appropriate for 12-to-14-year-olds
curl -s "http://localhost:8080/api/search?minAge=12&maxAge=14"
```

### 4.5. Date Filter
Shows courses scheduled on or after the specified ISO-8601 date.
```bash
curl -s "http://localhost:8080/api/search?startDate=2025-08-01T00:00:00Z"
```

### 4.6. Sorting and Pagination
- `sort`: Valid options are `upcoming` (default), `priceAsc`, `priceDesc`.
- `page` & `size`: Used for pagination (0-indexed).
```bash
curl -s "http://localhost:8080/api/search?sort=priceAsc&page=1&size=5"
```

### 4.7. Combined Query Example
```bash
curl -s "http://localhost:8080/api/search?q=programming&category=Programming&minPrice=20&maxPrice=80&sort=priceAsc&page=0&size=5"
```

---

## 5. Bonus Features (Assignment B: Autocomplete & Fuzzy Search)

### 5.1. Fuzzy Search Enhancements
The `GET /api/search?q={keyword}` endpoint utilizes `fuzziness("AUTO")`. It handles slight misspellings or typos gracefully.

**Example Request:**
```bash
# Searching for "digitl" instead of "digital"
curl -s "http://localhost:8080/api/search?q=digitl"
```

**Expected Response:**
*(Returns "Digital Photography Basics" despite the typo)*
```json
{
  "total": 1,
  "courses": [
    {
      "id": "7",
      "title": "Digital Photography Basics",
      "description": "Learn how to use your DSLR camera like a pro.",
      "category": "Art",
      "type": "ONE_TIME",
      "gradeRange": "7th-9th",
      "minAge": 12,
      "maxAge": 18,
      "price": 35.0,
      "nextSessionDate": "2025-06-25T10:00:00Z",
      "suggest": null
    }
  ]
}
```

### 5.2. Autocomplete Suggestions Endpoint
The `GET /api/search/suggest?q={partial}` endpoint utilizes Elasticsearch completion suggesters mapped to a specialized `suggest` index field. It returns up to 10 instantaneous search term completions based on course titles.

**Example Request:**
```bash
curl -s "http://localhost:8080/api/search/suggest?q=Intro"
```

**Expected Response:**
*(Returns an exact array of suggested completions)*
```json
[
  "Intro to Artificial Intelligence",
  "Introduction to Psychology",
  "Introduction to Python Programming"
]
```
