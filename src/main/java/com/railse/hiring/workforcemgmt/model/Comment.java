package com.railse.hiring.workforcemgmt.model;

import lombok.Data;

@Data
public class Comment {
    private String author;
    private String message;
    private Long timestamp;
}
