package com.expensive.Expensive.Tracker.dto.resourceAction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateResourceActionRequest {

    @NotNull(message = "resourceId is required")
    @Min(value = 1, message = "resourceId must be greater than 0")
    private Long resourceId;

    @NotEmpty(message = "At least one action is required")
    private List<
            @NotNull(message = "actionId cannot be null")
            @Min(value = 1, message = "actionId must be greater than 0")
                    Long
            > actionIds;
}