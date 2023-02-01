package study.querydsl.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.entity.Member;
import study.querydsl.repository.support.Querydsl5RepositorySupport;

import java.util.List;

import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@Repository
public class MemberTestRepository extends Querydsl5RepositorySupport {
    public MemberTestRepository() {
        super(Member.class);
    }

    public List<Member> basicSelect() {
        return select(member)
                .from(member)
                .fetch();
    }

    public List<Member> basicSelectFrom() {
        return selectFrom(member)
                .fetch();
    }

    public Page<Member> searchPageByApplyPage(MemberSearchCondition condition, Pageable pageabe) {
        JPAQuery<Member> query = selectFrom(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition));
        List<Member> content = getQuerydsl().applyPagination(pageabe, query).fetch();

        return PageableExecutionUtils.getPage(content, pageabe, query::fetchCount);
    }

    public Page<Member> applyPagination(MemberSearchCondition condition, Pageable pageable) {
        return applyPagination(pageable, query ->
                query.selectFrom(member)
                        .leftJoin(member.team, team)
                        .where(searchBuilder(condition)));
    }

    public Page<Member> applyPagination2(MemberSearchCondition condition, Pageable pageable) {
        return applyPagination(pageable,
                contentQuery -> contentQuery.selectFrom(member)
                        .leftJoin(member.team, team)
                        .where(searchBuilder(condition)),
                countQuery -> countQuery
                        .select(member.count())
                        .from(member)
                        .leftJoin(member.team, team)
                        .where(searchBuilder(condition)));
    }

    private BooleanExpression ageBetween(int ageLoe, int ageGoe) {
        return ageLoe(ageLoe).and(ageGoe(ageGoe));
    }

    private BooleanExpression usernameEq(String username) {
        return StringUtils.hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return StringUtils.hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer age) {
        return age != null ? member.age.goe(age) : null;
    }

    private BooleanExpression ageLoe(Integer age) {
        return age != null ? member.age.loe(age) : null;
    }

    private BooleanBuilder searchBuilder(MemberSearchCondition searchCondition) {
        BooleanBuilder builder = new BooleanBuilder();
        return builder.and(usernameEq(searchCondition.getUsername()))
                .and(teamNameEq(searchCondition.getTeamName()))
                .and(ageGoe(searchCondition.getAgeGoe()))
                .and(ageLoe(searchCondition.getAgeLoe()));
    }
}
