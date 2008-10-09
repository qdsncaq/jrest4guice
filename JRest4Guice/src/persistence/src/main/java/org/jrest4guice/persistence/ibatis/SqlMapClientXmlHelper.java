package org.jrest4guice.persistence.ibatis;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import org.apache.commons.lang.ObjectUtils.Null;
import org.jrest4guice.persistence.ibatis.annotations.Delete;
import org.jrest4guice.persistence.ibatis.annotations.Insert;
import org.jrest4guice.persistence.ibatis.annotations.ParameterMap;
import org.jrest4guice.persistence.ibatis.annotations.Result;
import org.jrest4guice.persistence.ibatis.annotations.ResultMap;
import org.jrest4guice.persistence.ibatis.annotations.Select;
import org.jrest4guice.persistence.ibatis.annotations.Update;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

/**
 * 负责从类中提取SqlMap的配置信息，以xml字符串的形式返回
 * 
 * @author cnoss
 * 
 */
public class SqlMapClientXmlHelper {
	public static SqlMapping generateXmlConfig(Class<?> clazz) {

		SqlMapping mapping = new SqlMapping();

		// 处理参数映射字典
		StringBuffer parameterMapSb = new StringBuffer();
		if (clazz.isAnnotationPresent(ParameterMap.class)) {
			ParameterMap annotation = clazz.getAnnotation(ParameterMap.class);
			String id = annotation.id();
			Class<?> parameterClass = annotation.parameterClass();
			String[] parameters = annotation.parameters();

			parameterMapSb.append("  <parameterMap id=\"" + id + "\" class=\""
					+ parameterClass.getName() + "\">");
			for (String parameter : parameters) {
				parameterMapSb.append("\n    <parameter property=\"" + parameter
						+ "\"/>");
			}
			parameterMapSb.append("\n  </parameterMap>");
		}

		// 处理结果映射字典
		StringBuffer resultMapSb = new StringBuffer();
		if (clazz.isAnnotationPresent(ResultMap.class)) {
			ResultMap annotation = clazz.getAnnotation(ResultMap.class);
			String id = annotation.id();
			Class<?> resultClass = annotation.resultClass();
			Result[] results = annotation.result();

			resultMapSb.append("  <resultMap id=\"" + id + "\" class=\""
					+ resultClass.getName() + "\">");
			for (Result result : results) {
				resultMapSb.append("\n    <result property=\"" + result.property()
						+ "\"  column=\"" + result.column() + "\"/>");
			}
			resultMapSb.append("\n  </resultMap>");
		}

		// 处理Sql语句
		StringBuffer statementSb = new StringBuffer();
		Method[] methods = clazz.getDeclaredMethods();
		Select select;
		Update update;
		Insert insert;
		Delete delete;
		for (Method method : methods) {
			if (method.isAnnotationPresent(Select.class)) {
				select = method.getAnnotation(Select.class);
				generateMethodSqlMapping(statementSb, "select", method, select
						.id(), select.parameterMap(), select.parameterClass(),
						select.resltMap(), select.resltClass(), select.sql());
			} else if (method.isAnnotationPresent(Update.class)) {
				update = method.getAnnotation(Update.class);
				generateMethodSqlMapping(statementSb, "update", method, update
						.id(), null, update.parameterClass(), null, Null.class,
						update.sql());
			} else if (method.isAnnotationPresent(Insert.class)) {
				insert = method.getAnnotation(Insert.class);
				generateMethodSqlMapping(statementSb, "insert", method, insert
						.id(), null, insert.parameterClass(), null, Null.class,
						insert.sql());
			} else if (method.isAnnotationPresent(Delete.class)) {
				delete = method.getAnnotation(Delete.class);
				generateMethodSqlMapping(statementSb, "delete", method, delete
						.id(), null, delete.parameterClass(), null, Null.class,
						delete.sql());
			}
		}

		mapping.setParameterMap(parameterMapSb.toString());
		mapping.setResultMap(resultMapSb.toString());
		mapping.setStatement(statementSb.toString());
		return mapping;
	}

	private static void generateMethodSqlMapping(StringBuffer sb,
			String prefix, Method method, String id, String parameterMap,
			Class<?> parameterClazz, String resultMap, Class<?> resultClazz,
			String sql) {
		sb.append("\n");

		String param = "", result = "";
		Class<?>[] parameterTypes;
		Class<?> returnType;

		if (id.trim().equals("")) {
			id = method.getName();
		}

		if (parameterMap != null && !parameterMap.trim().equals("")) {
			param = " parameterMap=\"" + parameterMap + "\"";
		} else if (parameterClazz == Null.class) {
			parameterTypes = method.getParameterTypes();
			if (parameterTypes != null && parameterTypes.length > 0) {
				parameterClazz = parameterTypes[0];
			}
			if (parameterClazz != Null.class) {
				param = " parameterClass=\"" + parameterClazz.getName() + "\"";
			} else
				param = "";
		}

		if (resultMap != null && !resultMap.trim().equals("")) {
			param = " resultMap=\"" + resultMap + "\"";
		} else if (resultClazz == Null.class) {
			returnType = method.getReturnType();
			if (!returnType.getName().equals("void")) {
				resultClazz = returnType;
				final Type genericType = method.getGenericReturnType();
				if (genericType instanceof ParameterizedTypeImpl) {
					ParameterizedTypeImpl pgType = (ParameterizedTypeImpl) genericType;
					Type[] actualTypeArguments = pgType
							.getActualTypeArguments();
					resultClazz = (Class<?>) actualTypeArguments[0];
				}
			}
			if (resultClazz != Null.class) {
				result = " resultClass=\"" + resultClazz.getName() + "\"";
			} else
				result = "";
		}

		sb.append("  <" + prefix + " id=\"" + id + "\"" + param + result + ">\n");
		sb.append("    "+sql.trim());
		sb.append("\n  </" + prefix + ">");
	}
}
