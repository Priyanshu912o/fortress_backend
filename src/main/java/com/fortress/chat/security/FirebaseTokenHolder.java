package com.fortress.chat.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Holds the decoded Firebase token info (uid + email) for the current request.
 * Used as the Authentication principal in SecurityContext.
 */
@Getter
@AllArgsConstructor
public class FirebaseTokenHolder {
    private final String uid;
    private final String email;
}
