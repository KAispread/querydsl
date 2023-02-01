package study.querydsl.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.dto.QMemberTeamDto;
import study.querydsl.entity.Member;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Objects;

import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

public class MemberRepositoryCustomImpl extends QuerydslRepositorySupport implements MemberRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    public MemberRepositoryCustomImpl() {
        super(Member.class);
        queryFactory = new JPAQueryFactory(getEntityManager());
    }

    @Override
    public List<MemberTeamDto> search(MemberSearchCondition condition) {
        from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")
                ))
                .fetch();

        return queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")
                ))
                .from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .fetch();
    }

    @Override
    public Page<MemberTeamDto> searchPageSimple(MemberSearchCondition condition, Pageable pageable) {
        List<MemberTeamDto> result = queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")
                ))
                .from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition));

        return PageableExecutionUtils.getPage(result, pageable, countQuery::fetchOne);
    }

    // QuerydslRepositorySupport 사용
    public Page<MemberTeamDto> searchPageSimple2(MemberSearchCondition condition, Pageable pageable) {
        JPQLQuery<MemberTeamDto> query = from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")
                ));

        List<MemberTeamDto> result = Objects.requireNonNull(getQuerydsl()).applyPagination(pageable, query).fetch();

        JPQLQuery<Long> countQuery = from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .select(member.count());

        return PageableExecutionUtils.getPage(result, pageable, countQuery::fetchOne);
    }

    @Override
    public Page<MemberTeamDto> searchPageComplex(MemberSearchCondition condition, Pageable pageable) {
        List<MemberTeamDto> result = queryFactory
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")
                ))
                .from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .fetch();

        // 카운트 쿼리 최적화
        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition));

        return PageableExecutionUtils.getPage(result, pageable, countQuery::fetchOne);
    }

    private long getTotal(MemberSearchCondition condition) {
        return queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(searchBuilder(condition))
                .fetch().size();
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
