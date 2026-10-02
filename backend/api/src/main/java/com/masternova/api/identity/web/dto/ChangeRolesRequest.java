package com.masternova.api.identity.web.dto;

import com.masternova.api.identity.Role;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ChangeRolesRequest(@NotNull Set<Role> roles) {}
