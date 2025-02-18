package com.tencent.cloud.smh.api.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

/**
 * <p>
 * Created by rickenwang on 2021/8/26.
 * Copyright 2010-2021 Tencent Cloud. All Rights Reserved.
 */


data class AuthorizeToContent(
    val authorizeTo: List<AuthorityEntity>
)


data class AuthorityEntity(
    val roleId: Int,
    val spaceId: String?,
    val userId: Long?,
) {
    companion object {

        fun newTeamAuthorityEntity(role: Role, spaceId: String) = AuthorityEntity(
            roleId = role.id,
            spaceId = spaceId,
            userId = null,
        )

        fun newUserAuthorityEntity(role: Role, userId: Long) = AuthorityEntity(
            roleId = role.id,
            spaceId = null,
            userId = userId,
        )
    }
}

@Parcelize
open class Role(
    val id: Int,
    val name: String,
    val roleDesc: String = "",
    val isDefault: Boolean,
    val isOwner: Boolean,
): Parcelable

@Parcelize
data class MediaAuthority (

    val canView: Boolean,
    val canPreview: Boolean,
    val canDownload: Boolean,
    val canUpload: Boolean,
    val canDelete: Boolean,
    val canModify: Boolean,
    val canAuthorize: Boolean,
    val canShare: Boolean,
    val canPreviewSelf: Boolean,
    val canDownloadSelf: Boolean
): Parcelable {

    companion object {

        fun newOwnerAuthority() = MediaAuthority(
            canView = true,
            canPreview = true,
            canDownload = true,
            canUpload = true,
            canDelete = true,
            canModify = true,
            canAuthorize = true,
            canShare = true,
            canPreviewSelf = true,
            canDownloadSelf = true
        )
    }
}

@Parcelize
data class MediaAuthorityButton (
    val showViewButton: Boolean,
    val showPreviewButton: Boolean,
    val showDownloadButton: Boolean,
    val showUploadButton: Boolean,
    val showDeleteButton: Boolean,
    val showAuthorizeButton: Boolean,
    val showShareButton: Boolean,
    val showModifyButton: Boolean,
    val showMoveButton: Boolean,
    val showRenameButton: Boolean,
    val showCopyButton: Boolean,
    val showRestoreButton: Boolean
): Parcelable {

    companion object {

        fun newOwnerAuthorityButton() = MediaAuthorityButton(
            showViewButton = true,
            showPreviewButton = true,
            showDownloadButton = true,
            showUploadButton = true,
            showDeleteButton = true,
            showAuthorizeButton = true,
            showShareButton = true,
            showModifyButton = true,
            showMoveButton = true,
            showRenameButton = true,
            showCopyButton = true,
            showRestoreButton = true
        )
    }
}

@Parcelize
data class RecycledAuthority(
    val canDelete: Boolean,
    val canRestore: Boolean,
): Parcelable

data class AuthorizedContent(
    val totalNum: Int,
    val contents: List<AuthorizedItem>,
    val eTag: String? = null,
    val nextMarker: String? = null,
)


data class AuthorizedItem(
    val spaceId: String,
    val name: String,
    val type: MediaType,
    val userId: String,
    val creationTime: String,
    val modificationTime: String,
    val authorityList: MediaAuthority?,
    val authorityButtonList: MediaAuthorityButton?,
    val path: List<String>,
    val team: TeamBrief,
    val user: UserBrief,
    val localSync: LocalSync?,
)

@Parcelize
data class TeamBrief(
    val id: Int,
    val name: String,
    val spaceId: String,
    val parentId: Int,
): Parcelable

@Parcelize
data class UserBrief(
    val id: Int,
    val name: String,
): Parcelable

@Parcelize
open class AuthorityTag(
    val id: Int,
    val name: String,
): Parcelable {

    companion object {

        fun fromId(id: Int) = when (id) {
            1 -> InheritAuthority()
            2 -> DefaultAuthority()
            else -> NullAuthority()
        }
    }
}

@Parcelize
class NullAuthority: AuthorityTag(
    id = 0,
    name = "Null",
)

@Parcelize
class InheritAuthority: AuthorityTag(
    id = 1,
    name = "Inherit",
)

@Parcelize
class DefaultAuthority: AuthorityTag(
    id = 2,
    name = "Default",
)




