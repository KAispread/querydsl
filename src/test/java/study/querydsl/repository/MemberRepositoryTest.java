package study.querydsl.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.Team;

import javax.persistence.EntityManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
@SpringBootTest
class MemberRepositoryTest {
    @Autowired
    EntityManager em;

    @Autowired
    MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
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
    void basicTest() {
        Member member1 = new Member("member99", 10);
        memberRepository.save(member1);

        Member findMember = memberRepository.findById(member1.getId()).get();
        assertThat(findMember).isEqualTo(member1);

        List<Member> result1 = memberRepository.findAll();
        assertThat(result1).containsExactly(member1);

        List<Member> result2 = memberRepository.findByUsername("member1");
        assertThat(result2).containsExactly(member1);
    }

    @Test
    void search() {
        MemberSearchCondition condition = MemberSearchCondition.builder()
                .teamName("teamB")
                .ageLoe(40)
                .build();

        List<MemberTeamDto> result = memberRepository.search(condition);

        for (MemberTeamDto memberTeamDto : result) {
            System.out.println("memberTeamDto = " + memberTeamDto);
        }

        assertThat(result)
                .extracting("username")
                .contains("member4");
    }

    @Test
    void searchPageSimple() {
        MemberSearchCondition condition = MemberSearchCondition.builder().build();
        PageRequest pageRequest = PageRequest.of(0, 3);

        Page<MemberTeamDto> result = memberRepository.searchPageSimple(condition, pageRequest);

        assertThat(result.getSize()).isEqualTo(3);
        assertThat(result.getContent()).extracting("username")
                .containsExactly("member1", "member2", "member3");
    }
}