package study.querydsl;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
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
        //member1을 찾아라
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
        // 전체 조회
        List<Member> list = queryFactory
                .selectFrom(member)
                .fetch();

        // 단건 조회
        Member one = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        // 첫 번째 값 조회 limit(1).fetchOne()
        Member firstResult = queryFactory
                .selectFrom(member)
                .fetchFirst();

        // 페이징 쿼리
        PageRequest request = PageRequest.of(0, 3);
        List<Member> page = queryFactory
                .selectFrom(member)
                .where(member.username.startsWith("member"))
                .offset(request.getOffset())
                .limit(request.getPageSize())
                .fetch();

        // count 쿼리 예시
        int size = queryFactory
                .selectFrom(member)
                .where(member.username.like("member"))
                .fetch().size();
    }

    /*
    * 회원 정렬 순서
    * 1. 회원 나이 내림차순(desc)
    * 2. 회원 이름 오름차순 (asc)
    * 단 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
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
    * 팀 A에 소속된
    * 모든 회원
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
    * 세타 조인
    * 회원이 이름이 팀 이름과 같은 회원 조회
    * */
    @Test
    void theta_join() {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory
                .select(member)
                // from 절에 다른 엔티티 삽입 후 검색 조건에 추가
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /*
    * 예) 회원과 팀을 조인하면서, 팀 이름이 teamA 인 팀만 조인, 회원은 모두 조회
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
    * 연관관계 없는 엔티티 외부 조인
    * 회원의 이름이 팀 이름과 같은 대상 외부 조인
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

    // 페치조인 사용 X
    @Test
    void fetchJoinNO() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();
        // 로딩이 된 Entity 인지 판별
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치조인 미적용").isFalse();
    }

    // 페치 조인 O
    @Test
    void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                // Join 절 뒤에 fetchJoin() 메서드 사용
                .join(member.team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        // 로딩이 된 Entity 인지 판별
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치조인 미적용").isTrue();
    }

    /*
    * 서브 쿼리
    *
    * 나이가 가장 많은 회원 조회
    * 나이가 평균인 회원 조회
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
                                // in 절
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
    * Case 문
    * */
    @Test
    void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
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
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    /*
    * orderBy 에 Case 사용
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

    // 상수 추가
    @Test
    void constant() {
        List<Tuple> result = queryFactory
                // 상수
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    // 상수 더하기
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
    * 프로젝션 : select 절에 조회할 대상 지정
    * */
    @Test
    void simpleProjection() {
        // 프로젝션 대상이 하나
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }

        // 프로젝션 대상이 여러개
        // Repository 단에서 처리하도록 하는 것이 좋음 -> Service가 모르게
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
    * 프로젝션 Dto
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
        // Setter 주입 - Projections.bean
        // 기본 생성자 & setter 필수
        queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        // 필드 주입 - Projections.fields
        queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        // 필드명이 다를 때 [필드주입 & setter]
        //
        QMember memberSub = new QMember("memberSub");
        queryFactory
                .select(Projections.constructor(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(
                                // 서브쿼리를 날려, member 의 최대 나이를 age 에 매칭
                                // age -> UserDto.age
                                JPAExpressions
                                        .select(memberSub.age.max())
                                        .from(memberSub), "age"
                        )))
                .from(member)
                .fetch();

        // 생성자 주입 - Projections.constructor
        // Entity 필드와 Dto 필드의 타입이 맞아야 함
        queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();
    }
}
