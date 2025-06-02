package com.sav.authentification.model;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.*;

import lombok.*;

@SuppressWarnings("serial")
@Entity
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "user_type")
public class User implements UserDetails {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idUser;
	private String email;
	private String username;
	private String login;
	private String password;

	@Column(name = "num_sip", unique = true)
	private String numSIP;

	@Enumerated(EnumType.STRING)
	private Roles role;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(role.name()));
	}

	@Override
	public String getPassword() {
		return password;
	}

	public String getLogin() {
		return login;
	}

	public void setPassword(String pass) {
		this.password = pass;
	}

	public Roles getRole() {
		return role;
	}
}
