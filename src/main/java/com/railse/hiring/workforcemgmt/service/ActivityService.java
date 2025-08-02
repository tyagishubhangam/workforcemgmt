package com.railse.hiring.workforcemgmt.service;

import com.railse.hiring.workforcemgmt.model.Activity;

public interface ActivityService {

    void log( Long taskId,Activity activity);
}
