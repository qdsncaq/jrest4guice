package org.jrest.sample.entity;

import java.io.Serializable;
import java.sql.Time;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import org.hibernate.annotations.GenericGenerator;

@Entity()
@Table(name = "Contact_tb")
@NamedQueries( {
		@NamedQuery(name = "byDate", query = "select e from Contact e where e.changeDate<=:changeDate"),
		@NamedQuery(name = "byName", query = "select e from Contact e where e.name=:name"),
		@NamedQuery(name = "list", query = "select e from Contact e order by e.changeDate desc") })
public class Contact implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2422008878326999364L;

	@Id
	@GeneratedValue(generator = "system-uuid")
	@GenericGenerator(name = "system-uuid", strategy = "uuid")
	@Column(name = "id", nullable = false, updatable = false)
	private String id;

	@Column(name = "name", nullable = false, length = 36, unique = true)
	private String name;

	@Column(name = "headPic", nullable = true, length = 255)
	private String headPic;

	@Column(name = "homePhone", nullable = true, length = 20)
	private String homePhone;

	@Column(name = "mobilePhone", nullable = true, length = 20)
	private String mobilePhone;

	@Column(name = "eMail", nullable = true, length = 36)
	private String eMail;

	@Column(name = "address", nullable = true, length = 200)
	private String address;

	@Column(name = "changeDate", nullable = true)
	private Time changeDate = new Time(System.currentTimeMillis());

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHomePhone() {
		return homePhone;
	}

	public void setHomePhone(String homePhone) {
		this.homePhone = homePhone;
	}

	public String getMobilePhone() {
		return mobilePhone;
	}

	public void setMobilePhone(String mobilePhone) {
		this.mobilePhone = mobilePhone;
	}

	public String getEMail() {
		return eMail;
	}

	public void setEMail(String mail) {
		eMail = mail;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getHeadPic() {
		return headPic;
	}

	public void setHeadPic(String headPic) {
		this.headPic = headPic;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Contact other = (Contact) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
}