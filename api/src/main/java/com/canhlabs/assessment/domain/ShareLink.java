package com.canhlabs.assessment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.persistence.*;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Entity
@Table(name = "share_links")
public class ShareLink extends BaseDomain {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "url_link")
    private String urlLink;

    @Column(name = "embed_link")
    private String embedLink;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String desc;


    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="user_id", nullable=false)
    private User user;

    @Column(name = "up_count")
    private Long upCount;

    @Column(name = "down_count")
    private Long downCount;
}
