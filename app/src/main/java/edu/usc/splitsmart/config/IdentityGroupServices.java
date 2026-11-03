package edu.usc.splitsmart.config;

import edu.usc.splitsmart.service.AuthService;
import edu.usc.splitsmart.service.GroupService;

/** Available feature subset; avoids exposing unavailable feature implementations. */
public interface IdentityGroupServices {
    AuthService auth();
    GroupService groups();
}
