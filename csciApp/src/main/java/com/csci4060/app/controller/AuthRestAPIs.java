package com.csci4060.app.controller;

import java.util.HashSet;
import java.util.Set;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.csci4060.app.message.request.LoginForm;
import com.csci4060.app.message.request.SignUpForm;
import com.csci4060.app.message.response.JwtResponse;
import com.csci4060.app.model.APIresponse;
import com.csci4060.app.model.ConfirmationToken;
import com.csci4060.app.model.Role;
import com.csci4060.app.model.RoleName;
import com.csci4060.app.model.User;
import com.csci4060.app.repository.ConfirmationTokenRepository;
import com.csci4060.app.repository.RoleRepository;
import com.csci4060.app.repository.UserRepository;
import com.csci4060.app.security.jwt.JwtProvider;
import com.csci4060.app.services.EmailSenderService;

/*
 *  AuthRestAPIs.java defines 2 APIs:

/api/auth/signup: sign up
-> check username/email is already in use.
-> create User object
-> store to database
/api/auth/signin: sign in
-> attempt to authenticate with AuthenticationManager bean.
-> add authentication object to SecurityContextHolder
-> Generate JWT token, then return JWT to client

 */

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class AuthRestAPIs {

	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	UserRepository userRepository;

	@Autowired
	RoleRepository roleRepository;

	@Autowired
	private ConfirmationTokenRepository confirmationTokenRepository;

	@Autowired
	private EmailSenderService emailSenderService;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	JwtProvider jwtProvider;

	@PostMapping("/signin")
	public APIresponse authenticateUser(@Valid @RequestBody LoginForm loginRequest) {
		
		User user = userRepository.findByUsername(loginRequest.getUsername()).orElseThrow(
				()->new UsernameNotFoundException("User not found with -> username: "+loginRequest.getUsername()));
		
		if(user.isVerified()) {
			Authentication authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			String jwt = jwtProvider.generateJwtToken(authentication);
			return new APIresponse(HttpStatus.OK.value(), "Successful", new JwtResponse(jwt, loginRequest.getUsername()));
		}
		
		return new APIresponse(HttpStatus.FORBIDDEN.value(), "Please click on the verification link to login", null);
	}

	@PostMapping("/signup")
	public APIresponse registerUser(@Valid @RequestBody SignUpForm signUpRequest) {
		if (userRepository.existsByUsername(signUpRequest.getUsername())) {
			return new APIresponse(HttpStatus.BAD_REQUEST.value(), "Fail -> Username is already taken!", null);
		}

		if (userRepository.existsByEmail(signUpRequest.getEmail())) {
			return new APIresponse(HttpStatus.BAD_REQUEST.value(), "Fail -> Email is already in use!", null);
		}

		// Creating user's account
		User user = new User(signUpRequest.getName(), signUpRequest.getUsername(), signUpRequest.getEmail(),
				encoder.encode(signUpRequest.getPassword()), signUpRequest.isVerified());

		Set<String> strRoles = signUpRequest.getRole();
		Set<Role> roles = new HashSet<>();

		for (String each : strRoles) {

			if (each.equals("admin")) {
				Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
						.orElseThrow(() -> new RuntimeException("Fail! -> Cause: User Role not find."));
				roles.add(adminRole);
			} else if (each.equals("pm")) {
				Role pmRole = roleRepository.findByName(RoleName.ROLE_PM)
						.orElseThrow(() -> new RuntimeException("Fail! -> Cause: User Role not find."));
				roles.add(pmRole);
			} else {
				Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
						.orElseThrow(() -> new RuntimeException("Fail! -> Cause: User Role not find."));
				roles.add(userRole);
			}
		}

		user.setRoles(roles);
		userRepository.save(user);
		
		ConfirmationToken confirmationToken = new ConfirmationToken(user);

		confirmationTokenRepository.save(confirmationToken);

		SimpleMailMessage mailMessage = new SimpleMailMessage();
		mailMessage.setTo(user.getEmail());
		mailMessage.setSubject("Complete Registration!");
		mailMessage.setFrom("Rohan9841@gmail.com");
		mailMessage.setText("To confirm your account, please click here : "
				+ "http://localhost:8181/api/auth/confirm-account/" + confirmationToken.getConfirmationToken());

		emailSenderService.sendEmail(mailMessage);

		return new APIresponse(HttpStatus.OK.value(), "Verification code has been sent to you email address. Please click it to register successfully.", null);
	}

	@GetMapping("/confirm-account/{token}")
	public APIresponse confirmUserAccout(@PathVariable("token") String confirmationToken) {
		ConfirmationToken token = confirmationTokenRepository.findByConfirmationToken(confirmationToken);
		
		if(token != null)
        {
            User user = userRepository.findByEmailIgnoreCase(token.getUser().getEmail()).orElseThrow(
    				()->new UsernameNotFoundException("User not found with -> email: "+token.getUser().getEmail()));
            
            user.setVerified(true);
            
            userRepository.save(user);
            return new APIresponse(HttpStatus.OK.value(),"User registered successfully!", null);
        }
        else
        {
        	return new APIresponse(HttpStatus.UNAUTHORIZED.value(),"Verficication token is not in the database", null);
        }
	}

}
