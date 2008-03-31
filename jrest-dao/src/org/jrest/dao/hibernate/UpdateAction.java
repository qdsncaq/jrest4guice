package org.jrest.dao.hibernate;

import org.hibernate.Session;
import org.jrest.dao.actions.AbstractAction;
import org.jrest.dao.annotations.Update;

public class UpdateAction extends AbstractAction<Update, HibernateDaoContext> {

	@Override
	public Object execute(Object... parameters) {
		if (parameters.length == 0)
			return null;
		Session session = this.getContext().getSession();
		for (Object entity : parameters) {
			session.update(entity);
		}
		return null;
	}

	@Override
	protected void initialize() {
		this.annotationClass = Update.class;
		this.contextClass = HibernateDaoContext.class;
	}

}
