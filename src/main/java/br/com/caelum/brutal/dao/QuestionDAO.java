package br.com.caelum.brutal.dao;

import static org.hibernate.criterion.Order.desc;
import static org.hibernate.criterion.Projections.rowCount;
import static org.hibernate.criterion.Restrictions.and;
import static org.hibernate.criterion.Restrictions.eq;
import static org.hibernate.criterion.Restrictions.gt;
import static org.hibernate.criterion.Restrictions.isNull;

import java.util.List;

import javax.inject.Inject;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.joda.time.DateTime;

import br.com.caelum.brutal.dao.WithUserPaginatedDAO.OrderType;
import br.com.caelum.brutal.dao.WithUserPaginatedDAO.UserRole;
import br.com.caelum.brutal.model.Question;
import br.com.caelum.brutal.model.Tag;
import br.com.caelum.brutal.model.User;
import br.com.caelum.brutal.model.interfaces.RssContent;

@SuppressWarnings("unchecked")
public class QuestionDAO implements PaginatableDAO {
    protected static final Integer PAGE_SIZE = 35;
	public static final long SPAM_BOUNDARY = -5;
	
	private Session session;
    private WithUserPaginatedDAO<Question> withAuthor;
	private InvisibleForUsersRule invisible;

	@Deprecated
	public QuestionDAO() {
	}

	@Inject
    public QuestionDAO(Session session, InvisibleForUsersRule invisible) {
        this.session = session;
		this.invisible = invisible;
		this.withAuthor = new WithUserPaginatedDAO<Question>(session, Question.class, UserRole.AUTHOR, invisible);
    }
    
    public void save(Question q) {
        session.save(q);
    }

	public Question getById(Long questionId) {
		return (Question) session.load(Question.class, questionId);
	}
	
	public List<Question> allVisible(Integer page) {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.createAlias("q.information", "qi")
				.createAlias("q.author", "qa")
				.createAlias("q.lastTouchedBy", "ql")
				.createAlias("q.solution", "s", Criteria.LEFT_JOIN)
				.createAlias("q.solution.information", "si", Criteria.LEFT_JOIN)
				.addOrder(desc("q.lastUpdatedAt"))
				.setFirstResult(firstResultOf(page))
				.setMaxResults(PAGE_SIZE);

		return addInvisibleFilter(criteria).list();
	}

	public List<Question> unsolvedVisible(Integer page) {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.add(isNull("q.solution"))
				.addOrder(Order.desc("q.lastUpdatedAt"))
				.setMaxResults(PAGE_SIZE)
				.setFirstResult(firstResultOf(page))
				.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
				
		return addInvisibleFilter(criteria).list();
	}
	
	public List<Question> unanswered(Integer page) {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.add(Restrictions.eq("q.answerCount", 0l))
				.addOrder(Order.desc("q.lastUpdatedAt"))
				.setMaxResults(PAGE_SIZE)
				.setFirstResult(firstResultOf(page))
				.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		
		return addInvisibleFilter(criteria).list();
	}

	public Question load(Question question) {
		return getById(question.getId());
	}

	public List<Question> withTagVisible(Tag tag, Integer page, boolean semRespostas) {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.createAlias("q.information.tags", "t")
				.add(Restrictions.eq("t.id", tag.getId()))
				.addOrder(Order.desc("q.lastUpdatedAt"))
				.setFirstResult(firstResultOf(page))
				.setMaxResults(50)
				.setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY);
		
		if (semRespostas) {
			criteria.add(Restrictions.eq("q.answerCount", 0l));
		}

		
		return addInvisibleFilter(criteria).list();
	}
	
	public List<Question> postsToPaginateBy(User user, OrderType orderByWhat, Integer page) {
		return withAuthor.by(user,orderByWhat, page);
	}

	public List<RssContent> orderedByCreationDate(int maxResults) {
		return session.createCriteria(Question.class, "q")
				.add(Restrictions.eq("q.moderationOptions.invisible", false))
				.addOrder(Order.desc("q.createdAt"))
				.setMaxResults(maxResults)
				.list();
	}
	
	public List<RssContent> orderedByCreationDate(int maxResults, Tag tag) {
		return session.createCriteria(Question.class, "q")
				.createAlias("q.information.tags", "tags")
				.add(Restrictions.and(Restrictions.eq("q.moderationOptions.invisible", false), Restrictions.eq("tags.id", tag.getId())))
				.addOrder(Order.desc("q.createdAt"))
				.setMaxResults(maxResults)
				.list();
	}
	

	public List<Question> getRelatedTo(Question question) {
		return session.createCriteria(Question.class, "q")
				.createAlias("q.information.tags", "tags")
				.add(Restrictions.eq("tags.id", question.getMostImportantTag().getId()))
				.addOrder(Order.desc("q.createdAt"))
				.setMaxResults(5)
				.list();
	}

	public List<Question> hot(DateTime since, int count) {
		return session.createCriteria(Question.class, "q")
				.add(gt("q.createdAt", since))
				.add(and(Restrictions.eq("q.moderationOptions.invisible", false)))
				.addOrder(Order.desc("q.voteCount"))
				.setMaxResults(count)
				.list();
	}
	
	public List<Question> top(String section, int count) {
		Order order;
		if (section.equals("viewed")) {
			order = Order.desc("q.views");
		}
		else if (section.equals("answered")) {
			order = Order.desc("q.answerCount");
		}
		else /*if (section.equals("voted"))*/ {
			order = Order.desc("q.voteCount");
		}
		
		return session.createCriteria(Question.class, "q")
				.add(and(Restrictions.eq("q.moderationOptions.invisible", false)))
				.addOrder(order)
				.setMaxResults(count)
				.list();
	}
	
	public List<Question> randomUnanswered(DateTime after, DateTime before, int count) {
		return session.createCriteria(Question.class, "q")
				.add(and(isNull("q.solution"), Restrictions.between("q.createdAt", after, before)))
				.add(and(Restrictions.eq("q.moderationOptions.invisible", false)))
				.add(Restrictions.sqlRestriction("1=1 order by rand()"))
				.setMaxResults(count)
				.list();
	}

	public Long countWithAuthor(User user) {
		return withAuthor.count(user);
	}

	public Long numberOfPagesTo(User user) {
		return withAuthor.numberOfPagesTo(user);
	}
	
	public long numberOfPages() {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.setProjection(rowCount());
		Long totalItems = (Long) addInvisibleFilter(criteria).list().get(0);
		return calculatePages(totalItems);
	}

	public long numberOfPages(Tag tag) {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.createAlias("q.information", "qi")
				.createAlias("qi.tags", "t")
				.add(eq("t.id", tag.getId()))
				.setProjection(rowCount());
		Long totalItems = (Long) addInvisibleFilter(criteria).list().get(0);
		return calculatePages(totalItems);
	}

	public Long totalPagesUnsolvedVisible() {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.add(isNull("q.solution"))
				.setProjection(rowCount());
		Long result = (Long) addInvisibleFilter(criteria).list().get(0);
		return calculatePages(result);
	}
	
	public Long totalPagesWithoutAnswers() {
		Criteria criteria = session.createCriteria(Question.class, "q")
				.add(Restrictions.eq("q.answerCount", 0l))
				.setProjection(rowCount());
		Long result = (Long) addInvisibleFilter(criteria).list().get(0);
		return calculatePages(result);
	}

	private int firstResultOf(Integer page) {
		return PAGE_SIZE * (page-1);
	}

	private long calculatePages(Long count) {
		long result = count/PAGE_SIZE.longValue();
		if (count % PAGE_SIZE.longValue() != 0) {
			result++;
		}
		return result;
	}

	private Criteria addInvisibleFilter(Criteria criteria) {
		return invisible.addFilter("q", criteria);
	}

	public List<Question> withTagVisible(Tag tag, int page) {
		return withTagVisible(tag, page, false);
	}

}

