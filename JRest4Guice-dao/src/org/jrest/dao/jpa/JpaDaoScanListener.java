package org.jrest.dao.jpa;

import java.util.ArrayList;
import java.util.List;

import org.jrest.core.util.ClassScanListener;
import org.jrest.dao.DaoProvider;
import org.jrest.dao.annotations.Dao;

import com.google.inject.Binder;
import com.google.inject.Module;

@SuppressWarnings("unchecked")
public class JpaDaoScanListener implements ClassScanListener {
	final List<Class<?>> daos;
	
	public JpaDaoScanListener(){
		daos = new ArrayList<Class<?>>(0);
	}
	@Override
	public void onComplete(List<Module> modules) {
		Module module = new Module(){
			@Override
			public void configure(Binder binder) {
				for(Class clazz :daos){
					binder.bind(clazz).toProvider(DaoProvider.create(clazz));
				}
			}
		};
		
		modules.add(module);
	}

	@Override
	public void onScan(Class<?> clazz) {
		if(clazz.isAnnotationPresent(Dao.class))
			daos.add(clazz);
	}

	@Override
	public void onStart() {
	}
}
