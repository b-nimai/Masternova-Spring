package com.masternova.api.identity.web;

import com.masternova.api.identity.CurrentUser;
import com.masternova.api.identity.application.UserAdminService;
import com.masternova.api.identity.web.dto.ChangeRolesRequest;
import com.masternova.api.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN-only user management. The authorisation rule lives on UserAdminService. */
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

  private final UserAdminService admin;

  AdminUserController(UserAdminService admin) {
    this.admin = admin;
  }

  @GetMapping("/{id}")
  UserResponse get(@PathVariable UUID id) {
    return UserResponse.from(admin.get(id));
  }

  @PutMapping("/{id}/roles")
  UserResponse changeRoles(
      @PathVariable UUID id, @Valid @RequestBody ChangeRolesRequest request, CurrentUser me) {
    return UserResponse.from(admin.changeRoles(me, id, request.roles()));
  }
}
