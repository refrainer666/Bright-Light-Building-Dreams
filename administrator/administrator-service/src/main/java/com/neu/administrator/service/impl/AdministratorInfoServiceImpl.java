package com.neu.administrator.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neu.administrator.mapper.*;
import com.neu.administrator.model.dto.SearchOrgRequest;
import com.neu.administrator.model.dto.TaskDto;
import com.neu.administrator.model.es.PageResult;
import com.neu.administrator.model.es.RequestParams;
import com.neu.administrator.model.es.SearchVolParams;
import com.neu.administrator.model.po.Administrator;
import com.neu.administrator.model.po.Child;
import com.neu.administrator.model.po.Organization;
import com.neu.administrator.model.po.Volunteer;
import com.neu.administrator.service.AdministratorInfoService;
import com.neu.base.exception.BlbdException;

import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.ExistsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdministratorInfoServiceImpl extends ServiceImpl<AdministratorMapper, Administrator> implements AdministratorInfoService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ChildMapper childMapper;

    @Autowired
    private VolunteerMapper volunteerMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private OrganizationMapper organizationMapper;


    public PageResult search(RequestParams params) {
        try {
            //1.准备Request
            SearchRequest request = new SearchRequest("child");
            //2.准备请求参数
            //2.1 query
            buildBasicQuery(params, request);
            //2.2分页
            int page = params.getPage();
            int size = params.getSize();
            request.source().from((page - 1) * size).size(size);
            //todo 排序
            //3.发送请求
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            //解析响应
            return handleResponse(response);

        } catch (IOException e) {
            throw new BlbdException("搜索失败");
        }
    }

    @Override
    public PageResult searchChildInNeed(RequestParams params) {
        try {
            //1.准备Request
            SearchRequest request = new SearchRequest("child");
            //2.准备请求参数
            //2.1 query
            buildChildInNeedQuery(params, request);
            //2.2分页
            int page = params.getPage();
            int size = params.getSize();
            request.source().from((page - 1) * size).size(size);
            //todo 排序
            //3.发送请求
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            //解析响应
            return handleResponse(response);

        } catch (IOException e) {
            throw new BlbdException("搜索失败");
        }
    }

    @Override
    public PageResult searchChildNotAssign(TaskDto params) {
        String taskId= params.getTaskId();
        //查询分配任务的孩子的id
        //List<String> childIds=taskMapper.selectChildIdsByTaskId(taskId);
        //查询出不是这些id的孩子
        List<Child> children=childMapper.selectNotAssignChildren(taskId);

        return new PageResult((long) children.size(),children);
    }

    @Override
    public PageResult searchOrg(SearchOrgRequest params) {
        Integer size = params.getSize();
        Integer page = params.getPage();

        Organization organization=new Organization();
        if(!"".equals(params.getOrgName())&&null!=params.getOrgName()){
            organization.setOrgName("%"+params.getOrgName()+"%");
        }

        organization.setOrgPassIf(params.getPassed());


        System.out.println(organization);


        List<Organization> organizations=organizationMapper.selectOrgByPage(organization,(page-1)*size,size);
        int total=organizationMapper.countOrg(organization);
        return new PageResult((long) total,organizations);
    }


    private void buildChildInNeedQuery(RequestParams params, SearchRequest request) {
        //1.准备Boolean查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //1.1关键字搜索，match查询，放到must里
        //name
        String name = params.getName();
        if (StringUtils.isNoneBlank(name)) {
            //不为空，根据关键字查询
            boolQuery.must(QueryBuilders.matchQuery("name", name));
        } else {
            //为空，查询全部
            boolQuery.must(QueryBuilders.matchAllQuery());
        }




        //volunteerId为空代表没有志愿者帮助
        ExistsQueryBuilder volunteerIdQuery = QueryBuilders.existsQuery("volunteerId");
        boolQuery.mustNot(volunteerIdQuery);


        //}


        //设置查询条件
        request.source().query(boolQuery);

    }

    private void buildBasicQuery(RequestParams params, SearchRequest request) throws IOException {
        //1.准备Boolean查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //1.1关键字搜索，match查询，放到must里
        //name
        String name = params.getName();
        if (StringUtils.isNoneBlank(name)) {
            //不为空，根据关键字查询
            boolQuery.must(QueryBuilders.matchQuery("name", name));
        } else {
            //为空，查询全部
            boolQuery.must(QueryBuilders.matchAllQuery());
        }




        //设置查询条件
        request.source().query(boolQuery);


    }


    private PageResult handleResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        //1.总条数
        long total = searchHits.getTotalHits().value;
        //2.获取文档数组
        SearchHit[] hits = searchHits.getHits();
        //3.遍历
        List<Child> children = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            //获取source
            String json = hit.getSourceAsString();
            //反序列化，非高亮的
            Child child = JSON.parseObject(json, Child.class);
            //todo 处理高亮结果
//            Map<String, HighlightField> map=hit.getHighlightFields();
//            if(map!=null && !map.isEmpty()) {
//                //根据字段名获取高凉解过
//                HighlightField highlightField = map.get("name");
//                if (highlightField != null) {
//                    //获取高亮结果字符串数组的第一个元素
//                    String hName = highlightField.getFragments()[0].toString();
//                    //把高亮结果放到对象中
//                    child.setName(hName);
//                }
//            }

            //todo 排序信息
//                Object[] sortValues=hit.getSortValues();
//                if(sortValues.length>0){
//                    child.setScore(Integer.parseInt(sortValues[0].toString()));
//                }
            //放入集合
            children.add(child);
        }
        return new PageResult(total, children);
    }

    //mq监听数据库
    public void deleteById(String id) {
        childMapper.deleteById(id);
    }

    public void saveById(Child child) {
        //从数据库里查询是否有该孩子
        Child temp = childMapper.selectById(child.getId());

        //如果孩子不存在,则保存
        if (temp == null) {
            childMapper.insert(child);
        } else {
            //如果孩子存在，则更新
            childMapper.updateById(child);
        }

    }


    //es
    //删除文档信息
    public void deleteByIdEs(String id) {
        try {
            //1.准备Request
            DeleteRequest request = new DeleteRequest("child", id);
            //2.发送请求
            restHighLevelClient.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new BlbdException("删除失败");
        }

    }

    //更新或新增文档信息
    public void saveByIdEs(Child child) {
        try {
            //从文档库里查询是否有该孩子
            GetRequest getRequest = new GetRequest("child").id(child.getId());
            boolean exist = restHighLevelClient.exists(getRequest, RequestOptions.DEFAULT);

            //如果孩子不存在,则保存
            if (!exist) {
                //创建request
                IndexRequest request = new IndexRequest("child").id(child.getId());
                //准备参数
                request.source(JSON.toJSONString(child), XContentType.JSON);
                //发送请求
                restHighLevelClient.index(request, RequestOptions.DEFAULT);
            } else {
                //如果孩子存在，则更新
                UpdateRequest request = new UpdateRequest("child", child.getId());
                //准备参数
                request.doc(JSON.toJSONString(child), XContentType.JSON);
                //发送请求
                restHighLevelClient.update(request, RequestOptions.DEFAULT);
            }

        } catch (IOException e) {
            throw new BlbdException("操作失败");
        }
    }


    //志愿者

    @Override
    public void deleteVolunteerById(String id) {
        volunteerMapper.deleteByVolId(id);
    }

    @Override
    public void saveVolunteerById(Volunteer volunteer) {
        //从数据库里查询是否有该志愿者
        Volunteer temp = volunteerMapper.selectByVolId(volunteer.getVolId());

        //如果孩子不存在,则保存
        if (temp == null) {
            volunteerMapper.insertVol(volunteer);
        } else {
            //如果孩子存在，则更新
            volunteerMapper.updateByVolId(volunteer);
        }
    }


//    志愿者

    @Override
    public PageResult searchVol(SearchVolParams params) {
        try {
            //1.准备Request
            SearchRequest request = new SearchRequest("volunteer");
            //2.准备请求参数
            //2.1 query
            buildVolBasicQuery(params, request);
            //2.2分页
            int page = params.getPage();
            int size = params.getSize();
            request.source().from((page - 1) * size).size(size);
            //todo 排序
            //3.发送请求
            SearchResponse response = restHighLevelClient.search(request, RequestOptions.DEFAULT);
            //解析响应
            return handleVolResponse(response);

        } catch (IOException e) {
            e.printStackTrace();
            throw new BlbdException("搜索失败");
        }
    }

    private void buildVolBasicQuery(SearchVolParams params, SearchRequest request) {
        //1.准备Boolean查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //1.1关键字搜索，match查询，放到must里
        //name
        String name = params.getVolName();
        String passed=params.getPassed();
        if (StringUtils.isNoneBlank(name)) {
            //不为空，根据关键字查询
            boolQuery.must(QueryBuilders.matchQuery("volName", name));
        } else {
            //为空，查询全部
            boolQuery.must(QueryBuilders.matchAllQuery());
        }

        if (StringUtils.isNoneBlank(passed)) {
            //不为空，根据关键字查询
            boolQuery.must(QueryBuilders.matchQuery("ifPass", passed));
        } else {
            //为空，查询全部
            boolQuery.must(QueryBuilders.matchAllQuery());
        }
        //设置查询条件
        request.source().query(boolQuery);

    }

    private PageResult handleVolResponse(SearchResponse response) {
        SearchHits searchHits = response.getHits();
        //1.总条数
        long total = searchHits.getTotalHits().value;
        //2.获取文档数组
        SearchHit[] hits = searchHits.getHits();
        //3.遍历
        List<Volunteer> volunteers = new ArrayList<>(hits.length);
        for (SearchHit hit : hits) {
            //获取source
            String json = hit.getSourceAsString();
            //反序列化，非高亮的
            Volunteer volunteer = JSON.parseObject(json, Volunteer.class);
            //todo 处理高亮结果
//            Map<String, HighlightField> map=hit.getHighlightFields();
//            if(map!=null && !map.isEmpty()) {
//                //根据字段名获取高凉解过
//                HighlightField highlightField = map.get("name");
//                if (highlightField != null) {
//                    //获取高亮结果字符串数组的第一个元素
//                    String hName = highlightField.getFragments()[0].toString();
//                    //把高亮结果放到对象中
//                    child.setName(hName);
//                }
//            }

            //todo 排序信息
//                Object[] sortValues=hit.getSortValues();
//                if(sortValues.length>0){
//                    child.setScore(Integer.parseInt(sortValues[0].toString()));
//                }
            //放入集合
            volunteers.add(volunteer);
        }
        return new PageResult(total, volunteers);
    }

    public void deleteVolunteerByIdEs(String id) {
        try {
            //1.准备Request
            DeleteRequest request = new DeleteRequest("volunteer", id);
            //2.发送请求
            restHighLevelClient.delete(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new BlbdException("删除失败");
        }

    }

    //更新或新增文档信息
    public void saveVolunteerByIdEs(Volunteer volunteer) {
        try {
            //从文档库里查询是否有该孩子
            GetRequest getRequest = new GetRequest("volunteer").id(volunteer.getVolId());
            boolean exist = restHighLevelClient.exists(getRequest, RequestOptions.DEFAULT);

            //如果志愿者不存在,则保存
            if (!exist) {
                //创建request
                IndexRequest request = new IndexRequest("volunteer").id(volunteer.getVolId());
                //准备参数
                request.source(JSON.toJSONString(volunteer), XContentType.JSON);
                //发送请求
                restHighLevelClient.index(request, RequestOptions.DEFAULT);
            } else {
                //如果孩子存在，则更新
                UpdateRequest request = new UpdateRequest("volunteer", volunteer.getVolId());
                //准备参数
                request.doc(JSON.toJSONString(volunteer), XContentType.JSON);
                //发送请求
                restHighLevelClient.update(request, RequestOptions.DEFAULT);
            }

        } catch (IOException e) {
            throw new BlbdException("操作失败");
        }
    }


}
