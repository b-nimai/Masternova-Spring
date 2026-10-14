package com.masternova.api.entitlement;

/** Why access was ALLOWED — the reason of the first policy that allowed it (LLD §3). */
public enum AccessGrant {
  ADMIN,
  OWNER,
  FREE_COURSE,
  PREVIEW_LECTURE,
  ENTITLED
}
