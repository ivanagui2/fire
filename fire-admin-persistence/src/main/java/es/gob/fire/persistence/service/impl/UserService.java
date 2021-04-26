/*
/*******************************************************************************
 * Copyright (C) 2018 MINHAFP, Gobierno de España
 * This program is licensed and may be used, modified and redistributed under the  terms
 * of the European Public License (EUPL), either version 1.1 or (at your option)
 * any later version as soon as they are approved by the European Commission.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and
 * more details.
 * You should have received a copy of the EUPL1.1 license
 * along with this program; if not, you may find it at
 * http:joinup.ec.europa.eu/software/page/eupl/licence-eupl
 ******************************************************************************/

/**
 * <b>File:</b><p>es.gob.fire.persistence.service.impl.UserService.java.</p> *
 * <b>Description:</b><p>Class that implements the communication with the operations of the persistence layer.</p>
 * <b>Project:</b><p>Application for signing documents of @firma suite systems.</p>
 * <b>Date:</b><p>15/06/2018.</p>
 * @author Gobierno de España.
 * @version 1.0, 15/06/2018.
 */
package es.gob.fire.persistence.service.impl;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.data.jpa.datatables.mapping.DataTablesInput;
import org.springframework.data.jpa.datatables.mapping.DataTablesOutput;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import es.gob.fire.commons.utils.UtilsStringChar;
import es.gob.fire.persistence.dto.UserDTO;
import es.gob.fire.persistence.dto.UserEditDTO;
import es.gob.fire.persistence.dto.UserPasswordDTO;
import es.gob.fire.persistence.entity.Rol;
import es.gob.fire.persistence.entity.User;
import es.gob.fire.persistence.repository.RolRepository;
import es.gob.fire.persistence.repository.UserRepository;
import es.gob.fire.persistence.repository.datatable.UserDataTablesRepository;
import es.gob.fire.persistence.service.IUserService;

/**
 * <p>Class that implements the communication with the operations of the persistence layer.</p>
 * <b>Project:</b><p>Application for signing documents of @firma suite systems.</p>
 * @version 1.0, 01/06/2020.
 */
@Service
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class UserService implements IUserService {

	/**
	 * Constant attribute that represents the value of the administrator permission.
	 */
	private static final String ROLE_ADMIN_PERMISSON = "1";
	
	/**
	 * Attribute that represents the injected interface that proves CRUD operations for the persistence.
	 */
	@Autowired
	private UserRepository repository;
	
	/**
	 * Attribute that represents the injected interface that proves CRUD operations for the persistence.
	 */
	@Autowired
	private RolRepository rolRepository;

	/**
	 * Attribute that represents the injected interface that provides CRUD operations for the persistence.
	 */
	@Autowired
	private UserDataTablesRepository dtRepository;
	

	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#getUsertByUserId(java.lang.Long)
	 */
	@Override
	public User getUserByUserId(Long userId) {
		return repository.findByUserId(userId);
	}

	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#saveUser(es.gob.fire.persistence.entity.User)
	 */
	@Override
	public User saveUser(User user) {
		return repository.save(user);
	}
	
	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.service.IUserService#saveUser(es.gob.fire.persistence.configuration.dto.UserDTO)
	 */
	@Override
	@Transactional
	public User saveUser(UserDTO userDto) {
		User user = null;
		if (userDto.getUserId() != null) {
			user = repository.findByUserId(userDto.getUserId());
		} else {
			user = new User();
		}
		if (!StringUtils.isEmpty(userDto.getPassword())) {
			String pwd = userDto.getPassword();
			BCryptPasswordEncoder bcpe = new BCryptPasswordEncoder();
			String hashPwd = bcpe.encode(pwd);

			user.setPassword(hashPwd);
		}
		
		user.setUserName(userDto.getLogin());
		user.setName(userDto.getName());
		user.setSurnames(userDto.getSurnames());
		user.setEmail(userDto.getEmail());
		user.setStartDate(new Date());
		user.setRol(rolRepository.findByRolId(userDto.getRolId()));
		user.setRenovationDate(new Date());
		user.setRoot(Boolean.FALSE);
		user.setPhone(userDto.getTelf());
		//TODO Rellenar los campos que faltan
		return repository.save(user);
	}
	
	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.service.IUserService#updateUser(es.gob.fire.persistence.configuration.dto.UserDTO)
	 */
	@Override
	@Transactional
	public User updateUser(UserEditDTO userDto) {
		
		User user = null;
		
		if (userDto.getIdUserFireEdit() != null) {
			user = repository.findByUserId(userDto.getIdUserFireEdit());
		} else {
			user = new User();
		}
		user.setName(userDto.getNameEdit());
		user.setSurnames(userDto.getSurnamesEdit());
		user.setUserName(userDto.getUsernameEdit());
		user.setEmail(userDto.getEmailEdit());
		user.setRol(rolRepository.findByRolId(userDto.getRolId()));
		user.setPhone(userDto.getTelfEdit());
		//TODO Rellenar los campos que faltan

		return repository.save(user);
	}

	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#deleteUser(java.lang.Long)
	 */
	@Override
	@Transactional
	public void deleteUser(Long userId) {
		repository.deleteById(userId);
	}
	
	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#getAllUse()
	 */
	@Override
	public List<User> getAllUser() {
		return repository.findAll();
	}

	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#getUserByUserName(java.lang.String)
	 */
	@Override
	public User getUserByUserName(final String userName) {
		return repository.findByUserName(userName);
	}
	
	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#getUserByRenovationCode(java.lang.String)
	 */
	@Override
	public User getUserByRenovationCode(final String renovationCode) {
		return repository.findByRenovationCode(renovationCode);
	}
	
	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#getUserByUserNameOrEmail(java.lang.String,java.lang.String)
	 */
	@Override
	public User getUserByUserNameOrEmail(final String userName, final String email) {
		return repository.findByUserNameOrEmail(userName, email);
	}

	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.persistence.services.IUserService#findAll(org.springframework.data.jpa.datatables.mapping.DataTablesInput)
	 */
	@Override
	public DataTablesOutput<User> getAllUser(DataTablesInput input) {
		return dtRepository.findAll(input);
	}
	
	/**
	 * {@inheritDoc}
	 * @see es.gob.fire.service.IUserService#changeUserPassword(es.gob.fire.persistence.configuration.dto.UserPasswordDTO)
	 */
	@Override
	@Transactional
	public String changeUserPassword(UserPasswordDTO userPasswordDto) {
		User user = repository.findByUserId(userPasswordDto.getIdUserFirePass());
		String result = null;
		String oldPwd = userPasswordDto.getOldPassword();
		String pwd = userPasswordDto.getPassword();
		BCryptPasswordEncoder bcpe = new BCryptPasswordEncoder();
		String hashPwd = bcpe.encode(pwd);
		try {
			if (bcpe.matches(oldPwd, user.getPassword())) {
				user.setPassword(hashPwd);
				repository.save(user);
				result = "0";
			} else {
				result = "-1";
			}
		} catch (Exception e) {
			result = "-2";
			throw e;
		}
		return result;	
	}

	/* (non-Javadoc)
	 * @see es.gob.fire.persistence.service.IUserService#getAllRol()
	 */
	@Override
	public List<Rol> getAllRol() {
		
		return rolRepository.findAll();
	}

	/* (non-Javadoc)
	 * @see es.gob.fire.persistence.service.IUserService#isAdminRol(java.lang.Long)
	 */
	@Override
	public boolean isAdminRol(Long idRol) {
		
		// Preguntar si permisos de administrador
		Rol rol = rolRepository.findByRolId(idRol);
		String[] permissions = rol.getPermissions()==null?new String[]{}:rol.getPermissions().split(UtilsStringChar.SYMBOL_COMMA_STRING);
		Optional<String> optional = Arrays.stream(permissions).filter(x -> ROLE_ADMIN_PERMISSON.equals(x))
							.findFirst();
		
		return optional.isPresent();
	}

}