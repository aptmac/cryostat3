/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.security.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.inject.Alternative;
import javax.inject.Singleton;
import javax.ws.rs.core.HttpHeaders;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.arc.Priority;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.credential.PasswordCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Alternative
@Priority(0)
@IfBuildProfile("dev")
@Singleton
public class DevBasicAuthMechanism implements HttpAuthenticationMechanism {

    private static final Pattern BASIC_HEADER_PATTERN =
            Pattern.compile(
                    "^Basic[\\s]+([a-zA-Z0-9+=/]+$)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public Uni<SecurityIdentity> authenticate(
            RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (!context.request().headers().contains(HttpHeaders.AUTHORIZATION)) {
            return Uni.createFrom().optional(Optional.empty());
        }
        String header = context.request().getHeader(HttpHeaders.AUTHORIZATION);
        Matcher m = BASIC_HEADER_PATTERN.matcher(header);
        if (!m.find()) {
            return Uni.createFrom().failure(new UnauthorizedException());
        }
        String cred = m.group(1);
        String[] parts =
                new String(Base64.getDecoder().decode(cred), StandardCharsets.UTF_8).split(":");
        if (parts.length != 2) {
            return Uni.createFrom().failure(new UnauthorizedException());
        }
        return identityProviderManager.authenticate(
                new UsernamePasswordAuthenticationRequest(
                        parts[0], new PasswordCredential(parts[1].toCharArray())));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        int statusCode = HttpResponseStatus.UNAUTHORIZED.code();
        String headerName = "X-" + HttpHeaders.WWW_AUTHENTICATE;
        String content = "Basic";
        var cd = new ChallengeData(statusCode, headerName, content);
        return Uni.createFrom().item(cd);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(UsernamePasswordAuthenticationRequest.class);
    }
}