package warmer.redisapp.demo.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import warmer.redisapp.demo.consts.RedisKey;
import warmer.redisapp.demo.util.RedisUtil;

import java.util.Map;

@Controller
@RequestMapping("/")
public class HomeController{

	@Autowired
	private RedisUtil redisUtil;

	@RequestMapping("/detail/{articleId}")
	public String detail(@PathVariable("articleId")int articleId, Model model) {
		//记录浏览量到redis,然后定时更新到数据库
		String key=RedisKey.ARTICLE_VIEWCOUNT_CODE+articleId;
		//找到redis中该篇文章的点赞数，如果不存在则向redis中添加一条
		Map<Object,Object> viewCountItem=redisUtil.hmget(RedisKey.ARTICLE_VIEWCOUNT_KEY);
		Integer viewCount=0;
		if(!viewCountItem.isEmpty()){
			if(viewCountItem.containsKey(key)){
				viewCount=(Integer)viewCountItem.get(key);
				redisUtil.hset(RedisKey.ARTICLE_VIEWCOUNT_KEY,key,viewCount+1);
			}else {
				redisUtil.hset(RedisKey.ARTICLE_VIEWCOUNT_KEY,key,1);
			}
		}else{
			redisUtil.hset(RedisKey.ARTICLE_VIEWCOUNT_KEY,key,1);
		}
		return "home/detail";
	}

}