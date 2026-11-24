package org.scale.scale.ingest.model;

import java.util.List;
import java.util.Map;

import org.scale.scale.ingest.csv.ParsedCsvRow;
import org.springframework.stereotype.Component;

@Component
public class UserRowMapper implements RowMapper<User> {

    static final String USER_ID = "User_ID";
    static final String EDUCATION = "Education";
    static final String EXPERIENCE = "Experience";
    static final String AVAILABLE_TIME = "Available_Time";

    @Override
    public List<String> requiredHeaders() {
        return List.of(USER_ID, EDUCATION, EXPERIENCE, AVAILABLE_TIME);
    }

    @Override
    public String idColumn() {
        return USER_ID;
    }

    @Override
    public User map(ParsedCsvRow row) {
        return new User(
                RowFields.requireText(row, USER_ID),
                RowFields.requireText(row, EDUCATION),
                RowFields.requireText(row, EXPERIENCE),
                RowFields.requireNonNegativeNumber(row, AVAILABLE_TIME));
    }

    @Override
    public Map<String, Object> columnValues(User user) {
        return Map.of(
                USER_ID, user.userId(),
                EDUCATION, user.education(),
                EXPERIENCE, user.experience(),
                AVAILABLE_TIME, user.availableTime());
    }
}
