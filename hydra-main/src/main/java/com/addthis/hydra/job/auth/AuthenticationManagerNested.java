/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.job.auth;

import javax.annotation.Nonnull;

import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * In the nested authentication manager the inner manager
 * has precedence over the outer manager.
 *
 * If the user is authenticated using the inner manager
 * then the outer manager is used as a decorator to augment
 * the profile of the user. If the user is authenticated using
 * the inner manager then authentication is ignored by the outer manager.
 *
 * If the user is not authenticated using the inner manager
 * then authentication is performed using the outer manager.
 */
class AuthenticationManagerNested extends AuthenticationManager {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationManagerNested.class);

    @Nonnull
    final AuthenticationManager inner;

    @Nonnull
    final AuthenticationManager outer;

    @Nonnull
    final Closer closer;

    @JsonCreator
    public AuthenticationManagerNested(@JsonProperty("inner") AuthenticationManager inner,
                                       @JsonProperty("outer") AuthenticationManager outer) {
        this.inner = inner;
        this.outer = outer;
        this.closer = Closer.create();
        closer.register(outer);
        closer.register(inner);
        log.info("Registering nested authentication");
    }

    @Override boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        return (inner != null && inner.isAdmin(user)) || (outer != null && outer.isAdmin(user));
    }

    @Override String login(String username, String password, boolean ssl) {
        if ((username == null) || (password == null)) {
            return null;
        }
        String token = inner.login(username, password, ssl);
        if (token == null) {
            token = outer.login(username, password, ssl);
        }
        return token;
    }

    @Override public boolean verify(String username, String password, boolean ssl) {
        if ((username == null) || (password == null)) {
            return false;
        }
        return inner.verify(username, password, ssl) || outer.verify(username, password, ssl);
    }

    @Override User authenticate(String username, String secret) {
        if ((username == null) || (secret == null)) {
            return null;
        }
        User innerMatch = inner.authenticate(username, secret);
        User outerMatch;
        if (innerMatch != null) {
            outerMatch = outer.getUser(username);
        } else {
            outerMatch = outer.authenticate(username, secret);
        }
        return DefaultUser.join(innerMatch, outerMatch);
    }

    @Override User getUser(String username) {
        if (username == null) {
            return null;
        }
        User innerUser = inner.getUser(username);
        User outerUser = outer.getUser(username);
        return DefaultUser.join(innerUser, outerUser);
    }

    @Override String sudoToken(String username) {
        if (username == null) {
            return null;
        }
        String innerToken = inner.sudoToken(username);
        if (innerToken != null) {
            return innerToken;
        } else {
            return outer.sudoToken(username);
        }
    }

    @Override public void evict(String username) {
        inner.evict(username);
        outer.evict(username);
    }

    @Override void logout(String username, String secret) {
        inner.logout(username, secret);
        outer.logout(username, secret);
    }

    @Override
    public void close() throws IOException {
        closer.close();
    }

    @Override ImmutableList<String> adminGroups() {
        return ImmutableList.<String>builder().addAll(inner.adminGroups()).addAll(outer.adminGroups()).build();
    }

    @Override ImmutableList<String> adminUsers() {
        return ImmutableList.<String>builder().addAll(inner.adminUsers()).addAll(outer.adminGroups()).build();
    }

}
