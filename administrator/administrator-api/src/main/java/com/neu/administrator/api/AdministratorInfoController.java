package com.neu.administrator.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.neu.administrator.constants.MqConstants;
import com.neu.administrator.model.dto.QualificationsDto;
import com.neu.administrator.model.dto.SearchOrgRequest;
import com.neu.administrator.model.dto.TaskDto;
import com.neu.administrator.model.es.PageResult;
import com.neu.administrator.model.es.RequestParams;
import com.neu.administrator.model.es.SearchVolParams;
import com.neu.administrator.model.po.Administrator;
import com.neu.administrator.model.po.Child;
import com.neu.administrator.model.po.Volunteer;
import com.neu.administrator.service.AdministratorInfoService;
import com.neu.base.model.RestResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Api(value = "用户管理接口", tags = "管理员信息管理接口，提供管理员信息的增、删、改、查")
@RestController
@Slf4j
@RequestMapping("/user")
public class AdministratorInfoController {


    @Autowired
    private AdministratorInfoService administratorInfoService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @ApiOperation("管理员登录接口")
    @PostMapping("/login")
    public RestResponse<Administrator> login(HttpServletRequest request , @RequestBody Administrator administrator){
        //获取账户
        String username=administrator.getUsername();//前端获取的账户
        String password=administrator.getPassword();//前端获取的密码
        //判断账户是否存在
        LambdaQueryWrapper<Administrator> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(Administrator::getUsername,username);//查询条件
        //从数据库查询获取账户信息
        Administrator admin= administratorInfoService.getOne(queryWrapper);
        if(admin==null){
            return RestResponse.validfail("账户不存在");
        }
        //如果账户存在则进行密码比对
        if(!admin.getPassword().equals(password)){
            return RestResponse.validfail("密码错误，请重新输入");
        }
        //否则账号正常，登录成功,存入session
        request.getSession().setAttribute("administrator",admin.getId());
        //返回该用户的信息
        return RestResponse.success(admin);
    }

    @ApiOperation("管理员退出登录接口")
    @PostMapping("/logout")
    public RestResponse<String> logout(HttpServletRequest request){
        //清除session
        request.getSession().removeAttribute("administrator");
        return RestResponse.success("退出登录成功");
    }

    @ApiOperation("管理员注册接口")
    @PostMapping("/register")
    public RestResponse<String> register(HttpServletRequest request,@RequestBody Administrator administrator){

        //从前端获取账号信息
        Administrator admin=new Administrator();
        BeanUtils.copyProperties(administrator,admin);
        //todo 账号密码符合规范验证,前端耗时后端
        //存入数据库
        boolean flag=administratorInfoService.save(admin);
        if(flag){
            return RestResponse.success("注册成功");
        }else{
            return RestResponse.validfail("注册失败");
        }
    }


    @ApiOperation("删除孩子数据")
    @PostMapping("/delete")
    public RestResponse<String> deleteChildById(@RequestBody Child child){
//        administratorInfoService.deleteById(child.getId());
        administratorInfoService.deleteByIdEs(child.getId());
        //发送mq同步数据
        rabbitTemplate.convertAndSend(MqConstants.CHILD_EXCHANGE,MqConstants.CHILD_DELETE_KEY,child.getId());
        return RestResponse.success("删除成功");

    }

    @ApiOperation("新增或修改孩子数据")
    @PostMapping("/save")
    public RestResponse<String> saveChildById(@RequestBody Child child ){
        administratorInfoService.saveByIdEs(child);
        //发送mq同步数据
        rabbitTemplate.convertAndSend(MqConstants.CHILD_EXCHANGE,MqConstants.CHILD_INSERT_KEY,child);
//        administratorInfoService.saveById(child);
        return RestResponse.success("操作成功");
    }

    @ApiOperation("es分页搜索")
    @PostMapping("/search")
    public RestResponse<PageResult> search(@RequestBody RequestParams params){
        return RestResponse.success(administratorInfoService.search(params));
    }


    @ApiOperation("es分页需搜索需要帮助的孩子")
    @PostMapping("/search/childInNeed")
    public RestResponse<PageResult> searchChildInNeed(@RequestBody RequestParams params){
        return RestResponse.success(administratorInfoService.searchChildInNeed(params));
    }

    @ApiOperation("搜索还没有分配学习任务的的孩子")
    @PostMapping("/search/childNotAssign")
    public RestResponse<PageResult> searchChildInNeed(@RequestBody TaskDto params){
        return RestResponse.success(administratorInfoService.searchChildNotAssign(params));
    }


    @ApiOperation("删除志愿者数据")
    @PostMapping("/volunteer/delete")
    public RestResponse<String> deleteVolunteerById(String id){
        administratorInfoService.deleteVolunteerById(id);
        rabbitTemplate.convertAndSend(MqConstants.VOL_EXCHANGE,MqConstants.VOL_DELETE_KEY,id);
        return RestResponse.success("删除成功");
    }

    @ApiOperation("新增或修改es志愿者数据")
    @PostMapping("/volunteer/save")
    public RestResponse<String> saveVolunteerById(@RequestBody Volunteer volunteer ){
        administratorInfoService.saveVolunteerById(volunteer);
        rabbitTemplate.convertAndSend(MqConstants.VOL_EXCHANGE,MqConstants.VOL_INSERT_KEY,volunteer);
        return RestResponse.success("操作成功");
    }

    @ApiOperation("es查询志愿者")
    @PostMapping("/volunteer/search")
    public RestResponse<PageResult> searchVol(@RequestBody SearchVolParams params){

        return RestResponse.success(administratorInfoService.searchVol(params));
    }

    @ApiOperation("分页搜索组织")
    @PostMapping("/organization/search")
    public RestResponse<PageResult> searchOrg(@RequestBody SearchOrgRequest params){

        return RestResponse.success(administratorInfoService.searchOrg(params));
    }



}
