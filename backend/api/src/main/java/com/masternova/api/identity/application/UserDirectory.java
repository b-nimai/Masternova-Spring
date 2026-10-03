package com.masternova.api.identity.application;

import com.masternova.api.identity.IdentityApi;
import com.masternova.api.identity.domain.User;
import com.masternova.api.identity.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The implementation behind identity's public {@link IdentityApi}. */
@Service
class UserDirectory implements IdentityApi {

  private final UserRepository users;

  UserDirectory(UserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> displayName(UUID userId) {
    return users.findById(userId).map(User::displayName);
  }
}
