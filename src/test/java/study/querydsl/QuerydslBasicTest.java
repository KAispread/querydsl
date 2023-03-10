package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {
    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        queryFactory = new JPAQueryFactory(em);

        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamB);
        em.persist(teamA);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
        em.flush();
        em.clear();
    }

    @Test
    void startJPQL() {
        //member1??? ?????????
        String qlString = "select m from Member m where m.username = :username";
        Member member = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();
        assertThat(member.getUsername()).isEqualTo("member1");
    }

    @Test
    void queryDSL() {
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void search() {
        List<Member> members = queryFactory
                .selectFrom(member)
                .where(
                        member.username.contains("member"),
                        member.age.between(10, 30)
                )
                .fetch();

        for (Member member1 : members) {
            System.out.println("member1.getUsername() = " + member1.getUsername());
        }

        assertThat(members.size()).isEqualTo(3);
    }

    @Test
    void result() {
        // ?????? ??????
        List<Member> list = queryFactory
                .selectFrom(member)
                .fetch();

        // ?????? ??????
        Member one = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // ??? ?????? ??? ?????? limit(1).fetchOne()
        Member firstResult = queryFactory
                .selectFrom(member)
                .fetchFirst();

        // ????????? ??????
        PageRequest request = PageRequest.of(0, 3);
        List<Member> page = queryFactory
                .selectFrom(member)
                .where(member.username.startsWith("member"))
                .offset(request.getOffset())
                .limit(request.getPageSize())
                .fetch();

        // count ?????? ??????
        int size = queryFactory
                .selectFrom(member)
                .where(member.username.like("member"))
                .fetch().size();
    }

    /*
    * ?????? ?????? ??????
    * 1. ?????? ?????? ????????????(desc)
    * 2. ?????? ?????? ???????????? (asc)
    * ??? 2?????? ?????? ????????? ????????? ???????????? ??????(nulls last)
    * */
    @Test
    void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member nullMember = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(nullMember.getUsername()).isNull();
    }

    @Test
    void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(2)
                .limit(2)
                .fetch();

        for (Member member : result) {
            System.out.println("member.getUsername() = " + member.getUsername());
        }
        assertThat(result.size()).isEqualTo(2);
    }

    @Test
    void aggregation() {
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.min(),
                        member.age.max()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
    }

    @Test
    void group() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /*
    * ??? A??? ?????????
    * ?????? ??????
    * */
    @Test
    void join() {
        List<Member> result = queryFactory
                .selectFrom(member)

                .join(member.team, team)
                .leftJoin(member.team, team)
                .rightJoin(member.team, team)

                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /*
    * ?????? ??????
    * ????????? ????????? ??? ????????? ?????? ?????? ??????
    * */
    @Test
    void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                // from ?????? ?????? ????????? ?????? ??? ?????? ????????? ??????
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /*
    * ???) ????????? ?????? ???????????????, ??? ????????? teamA ??? ?????? ??????, ????????? ?????? ??????
    * JPQL: select m, t from Member m left join m.team t on t.name = 'teamA'
    * */
    @Test
    void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*
    * ???????????? ?????? ????????? ?????? ??????
    * ????????? ????????? ??? ????????? ?????? ?????? ?????? ??????
    * */
    @Test
    void join_on_no_relation() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .where(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    // ???????????? ?????? X
    @Test
    void fetchJoinNO() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        // ????????? ??? Entity ?????? ??????
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("???????????? ?????????").isFalse();
    }

    // ?????? ?????? O
    @Test
    void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                // Join ??? ?????? fetchJoin() ????????? ??????
                .join(member.team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // ????????? ??? Entity ?????? ??????
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("???????????? ?????????").isTrue();
    }

    /*
    * ?????? ??????
    *
    * ????????? ?????? ?????? ?????? ??????
    * ????????? ????????? ?????? ??????
    * */
    @Test
    void subQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result)
                .extracting("age")
                .containsExactly(40);


        QMember members = new QMember("members");
        List<Member> result2 = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(members.age.avg())
                                .from(members)
                ))
                .fetch();

        assertThat(result2)
                .extracting("age")
                .containsExactly(30, 40);
    }

    @Test
    void subQueryIn() {
        QMember members = new QMember("members");

        List<Member> result2 = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(members.age.avg())
                                .from(members)
                                // in ???
                                .where(members.age.gt(10))
                ))
                .fetch();

        assertThat(result2)
                .extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /*
    * Case ???
    * */
    @Test
    void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("??????")
                        .when(20).then("?????????")
                        .otherwise("??????"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20???")
                        .when(member.age.between(21, 30)).then("21~30???")
                        .otherwise("??????"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /*
    * orderBy ??? Case ??????
    * */
    @Test
    void orderByCase() {
        NumberExpression<Integer> rank = new CaseBuilder()
                .when(member.age.between(0, 20)).then(1)
                .when(member.age.between(21, 30)).then(2)
                .otherwise(3);

        List<Member> result = queryFactory
                .select(member)
                .from(member)
                .orderBy(rank.desc())
                .fetch();

        for (Member s : result) {
            System.out.println("s = " + s);
        }
    }

    // ?????? ??????
    @Test
    void constant() {
        List<Tuple> result = queryFactory
                // ??????
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    // ?????? ?????????
    @Test
    void concat() {
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /*
    * ???????????? : select ?????? ????????? ?????? ??????
    * */
    @Test
    void simpleProjection() {
        // ???????????? ????????? ??????
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }

        // ???????????? ????????? ?????????
        // Repository ????????? ??????????????? ?????? ?????? ?????? -> Service??? ?????????
        List<Tuple> tuple = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple t : tuple) {
            String username = t.get(member.username);
            Integer age = t.get(member.age);

            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    /*
    * ???????????? Dto
    * */
    @Test
    void findDtoByJPQL() {
        List<MemberDto> jpqlResult = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : jpqlResult) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    void findDtoByQueryDsl() {
        // Setter ?????? - Projections.bean
        // ?????? ????????? & setter ??????
        queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        // ?????? ?????? - Projections.fields
        queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        // ???????????? ?????? ??? [???????????? & setter]
        //
        QMember memberSub = new QMember("memberSub");
        queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(
                                // ??????????????? ??????, member ??? ?????? ????????? age ??? ??????
                                // age -> UserDto.age
                                JPAExpressions
                                        .select(memberSub.age.max())
                                        .from(memberSub), "age"
                        )))
                .from(member)
                .fetch();

        // ????????? ?????? - Projections.constructor
        // Entity ????????? Dto ????????? ????????? ????????? ???
        queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
    }

    /*
    * @QueryProjection
    * */
    @Test
    void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /*
    * ?????? ??????
    * */
    @Test
    void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
        assertThat(result.size()).isEqualTo(1);
    }

    // ??????????????? null ?????? ?????? ????????? ???????????? ??????
    // BooleanBuilder
    private List<Member> searchMember1(String usernameParam, Integer ageParam) {
        BooleanBuilder builder = new BooleanBuilder();

        if (usernameParam != null) {
            builder.and(member.username.eq(usernameParam));
        }

        if (ageParam != null) {
            builder.and(member.age.eq(ageParam));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /*
    * ???????????? - where ?????? ???????????? ??????
    * */
    @Test
    void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(null, ageParam);
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
        assertThat(result.size()).isEqualTo(1);
    }

    /*
    * where ????????? null ??? ????????? ??????
    * */
    private List<Member> searchMember2(String usernameParam, Integer ageParam) {
        return queryFactory
                .selectFrom(member)
                // ???????????? ???????????? ??????
//                .where(usernameEq(usernameParam), ageEq(ageParam))
                // ????????? ?????? ??????
                .where(allEq(usernameParam, ageParam))
                .fetch();
    }

    // ???????????? ?????????????????? ?????? ???????????? ????????? ??????
    private BooleanExpression ageEq(Integer ageParam) {
        return ageParam != null ? member.age.eq(ageParam) : null;
    }

    private BooleanExpression usernameEq(String usernameParam) {
        return usernameParam != null ? member.username.eq(usernameParam) : null;
    }

    private BooleanBuilder allEq(String usernameParam, Integer ageParam) {
        BooleanBuilder builder = new BooleanBuilder();
        return builder.and(usernameEq(usernameParam)).and(ageEq(ageParam));
    }

    /*
    * ??????, ?????? ??????
    * */
    @Test
    @Rollback(value = false)
    void bulkUpdate() {
        // ?????? ????????? ????????? ??????????????? ???????????? ??????
        // DB??? ????????? ??????????????? ?????? ?????? ?????? ??????

        long count = queryFactory
                .update(member)
                .set(member.username, "?????????")
                .where(member.age.lt(28))
                .execute();

        em.flush();
        em.clear();
    }

    // ?????? ??????
    @Test
    void bulkAdd() {
        // ?????????
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1))
                .execute();

        // ?????????
        long execute = queryFactory
                .update(member)
                .set(member.age, member.age.multiply(3))
                .execute();
    }

    @Test
    void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(30))
                .execute();
    }

    /*
    * SQL function ??????
    * Dialect ??? ???????????? ????????? ?????? function ?????? ??????
    * */
    @Test
    void sqlFunction() {
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate("function('replace', {0}, {1}, {2})",
                                member.username, "member", "M"))
                .from(member)
                .fetch();
    }

    @Test
    void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})",
//                                member.username)
//                ))
                .where(member.username.eq(member.username.lower()))
                .fetch();
    }
}

