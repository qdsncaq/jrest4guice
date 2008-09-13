<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>联系人详细信息(CTL template)</title>
<!-- 系统缺省的CSS -->
<link href="/full/css/default.css" rel="stylesheet" type="text/css" />
</head>
<body>
	$set{contact=ctx.content}
	$set{action="/full/contact"}
	
	$if{contact.id==null}
		<h4>请输入联系人的相关信息</h4>
	$else
		<h4>修改联系人<font color=green>"${contact.name}"</font>的信息</h4>
		$set{action="/full/contacts/"+contact.id+"!update"}
	$end
	
	<form action="${action}" method="post">
		$if{contact.id}
			<input name="id" type="hidden" value="${contact.id}">
		$end
		<table>
			<tr>
				<td>姓    名</td><td><input name="name" type="text" value="${contact.name}"><span class="error">${context.invalidValueMap.name.message}</span></td>	
			</tr>
			<tr>
				<td>手    机</td><td><input name="mobilePhone" type="text" value="${contact.mobilePhone}"><span class="error">${context.invalidValueMap.mobilePhone.message}</span></td>	
			</tr>
			<tr>
				<td>电子邮箱</td><td><input name="email" type="text" value="${contact.email}"><span class="error">${context.invalidValueMap.email.message}</span></td>	
			</tr>
			<tr>
				<td>家庭住址</td><td><textarea name="address">${contact.address}</textarea><span class="error">${context.invalidValueMap.address.message}</span></td>
			</tr>
		</table>
		<br/>
		<input type="submit" value="确定">
		<input type="reset" value="取消">
	</form>
	
	<br>
	<img src="/full/images/go.gif" style="margin-bottom: -22px;"><a href="/full/contacts">返回联系人列表</a>
	</br>
</body>
</html>