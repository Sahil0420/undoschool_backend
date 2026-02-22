import json

with open("src/main/resources/sample-courses.json", "r") as f:
    courses = json.load(f)

for course in courses:
    course["suggest"] = {"input": [course["title"]]}

with open("src/main/resources/sample-courses.json", "w") as f:
    json.dump(courses, f, indent=2)
