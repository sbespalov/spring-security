/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.security.config.annotation.web.configurers;

import org.apache.http.HttpHeaders;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.test.SpringTestRule;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link LogoutConfigurer}
 *
 * @author Rob Winch
 * @author Eleftheria Stein
 */
public class LogoutConfigurerTests {

	@Rule
	public final SpringTestRule spring = new SpringTestRule();

	@Autowired
	MockMvc mvc;

	@Test
	public void configureWhenDefaultLogoutSuccessHandlerForHasNullLogoutHandlerThenException() {
		assertThatThrownBy(() -> this.spring.register(NullLogoutSuccessHandlerConfig.class).autowire())
				.isInstanceOf(BeanCreationException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@EnableWebSecurity
	static class NullLogoutSuccessHandlerConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.logout()
					.defaultLogoutSuccessHandlerFor(null, mock(RequestMatcher.class));
			// @formatter:on
		}
	}

	@Test
	public void configureWhenDefaultLogoutSuccessHandlerForHasNullMatcherThenException() {
		assertThatThrownBy(() -> this.spring.register(NullMatcherConfig.class).autowire())
				.isInstanceOf(BeanCreationException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@EnableWebSecurity
	static class NullMatcherConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.logout()
					.defaultLogoutSuccessHandlerFor(mock(LogoutSuccessHandler.class), null);
			// @formatter:on
		}
	}

	@Test
	public void configureWhenRegisteringObjectPostProcessorThenInvokedOnLogoutFilter() {
		this.spring.register(ObjectPostProcessorConfig.class).autowire();

		verify(ObjectPostProcessorConfig.objectPostProcessor)
				.postProcess(any(LogoutFilter.class));
	}

	@EnableWebSecurity
	static class ObjectPostProcessorConfig extends WebSecurityConfigurerAdapter {
		static ObjectPostProcessor<Object> objectPostProcessor = spy(ReflectingObjectPostProcessor.class);

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.logout();
			// @formatter:on
		}

		@Bean
		static ObjectPostProcessor<Object> objectPostProcessor() {
			return objectPostProcessor;
		}
	}

	static class ReflectingObjectPostProcessor implements ObjectPostProcessor<Object> {
		@Override
		public <O> O postProcess(O object) {
			return object;
		}
	}

	@Test
	public void logoutWhenInvokedTwiceThenUsesOriginalLogoutUrl() throws Exception {
		this.spring.register(DuplicateDoesNotOverrideConfig.class).autowire();

		this.mvc.perform(post("/custom/logout")
				.with(csrf()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@EnableWebSecurity
	static class DuplicateDoesNotOverrideConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.logout()
					.logoutUrl("/custom/logout")
					.and()
				.logout();
			// @formatter:on
		}

		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			// @formatter:off
			auth
				.inMemoryAuthentication();
			// @formatter:on
		}
	}

	// SEC-2311
	@Test
	public void logoutWhenGetRequestAndCsrfDisabledThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledConfig.class).autowire();

		this.mvc.perform(get("/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	public void logoutWhenPostRequestAndCsrfDisabledThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledConfig.class).autowire();

		this.mvc.perform(post("/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	public void logoutWhenPutRequestAndCsrfDisabledThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledConfig.class).autowire();

		this.mvc.perform(put("/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	public void logoutWhenDeleteRequestAndCsrfDisabledThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledConfig.class).autowire();

		this.mvc.perform(delete("/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@EnableWebSecurity
	static class CsrfDisabledConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.csrf()
					.disable()
				.logout();
			// @formatter:on
		}
	}

	@Test
	public void logoutWhenGetRequestAndCsrfDisabledAndCustomLogoutUrlThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledAndCustomLogoutConfig.class).autowire();

		this.mvc.perform(get("/custom/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	public void logoutWhenPostRequestAndCsrfDisabledAndCustomLogoutUrlThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledAndCustomLogoutConfig.class).autowire();

		this.mvc.perform(post("/custom/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	public void logoutWhenPutRequestAndCsrfDisabledAndCustomLogoutUrlThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledAndCustomLogoutConfig.class).autowire();

		this.mvc.perform(put("/custom/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	public void logoutWhenDeleteRequestAndCsrfDisabledAndCustomLogoutUrlThenRedirectsToLogin() throws Exception {
		this.spring.register(CsrfDisabledAndCustomLogoutConfig.class).autowire();

		this.mvc.perform(delete("/custom/logout"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@EnableWebSecurity
	static class CsrfDisabledAndCustomLogoutConfig extends WebSecurityConfigurerAdapter {

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.csrf()
					.disable()
				.logout()
					.logoutUrl("/custom/logout");
			// @formatter:on
		}
	}

	// SEC-3170
	@Test
	public void configureWhenLogoutHandlerNullThenException() {
		assertThatThrownBy(() -> this.spring.register(NullLogoutHandlerConfig.class).autowire())
				.isInstanceOf(BeanCreationException.class)
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

	@EnableWebSecurity
	static class NullLogoutHandlerConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.logout()
					.addLogoutHandler(null);
			// @formatter:on
		}
	}

	// SEC-3170
	@Test
	public void rememberMeWhenRememberMeServicesNotLogoutHandlerThenRedirectsToLogin() throws Exception {
		this.spring.register(RememberMeNoLogoutHandler.class).autowire();

		this.mvc.perform(post("/logout")
				.with(csrf()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	@EnableWebSecurity
	static class RememberMeNoLogoutHandler extends WebSecurityConfigurerAdapter {
		static RememberMeServices REMEMBER_ME = mock(RememberMeServices.class);

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			// @formatter:off
			http
				.rememberMe()
					.rememberMeServices(REMEMBER_ME);
			// @formatter:on
		}
	}

	@Test
	public void logoutWhenAcceptTextHtmlThenRedirectsToLogin() throws Exception {
		this.spring.register(BasicSecurityConfig.class).autowire();

		this.mvc.perform(post("/logout")
				.with(csrf())
				.with(user("user"))
				.header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	// gh-3282
	@Test
	public void logoutWhenAcceptApplicationJsonThenReturnsStatusNoContent() throws Exception {
		this.spring.register(BasicSecurityConfig.class).autowire();

		this.mvc.perform(post("/logout")
				.with(csrf())
				.with(user("user"))
				.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
				.andExpect(status().isNoContent());
	}

	// gh-4831
	@Test
	public void logoutWhenAcceptAllThenReturnsStatusNoContent() throws Exception {
		this.spring.register(BasicSecurityConfig.class).autowire();

		this.mvc.perform(post("/logout")
				.with(csrf())
				.with(user("user"))
				.header(HttpHeaders.ACCEPT, MediaType.ALL_VALUE))
				.andExpect(status().isNoContent());
	}

	// gh-3902
	@Test
	public void logoutWhenAcceptFromChromeThenRedirectsToLogin() throws Exception {
		this.spring.register(BasicSecurityConfig.class).autowire();

		this.mvc.perform(post("/logout")
				.with(csrf()).with(user("user"))
				.header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	// gh-3997
	@Test
	public void logoutWhenXMLHttpRequestThenReturnsStatusNoContent() throws Exception {
		this.spring.register(BasicSecurityConfig.class).autowire();

		this.mvc.perform(post("/logout")
				.with(csrf())
				.with(user("user"))
				.header(HttpHeaders.ACCEPT, "text/html,application/json")
				.header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isNoContent());
	}

	@EnableWebSecurity
	static class BasicSecurityConfig extends WebSecurityConfigurerAdapter {
	}
}
