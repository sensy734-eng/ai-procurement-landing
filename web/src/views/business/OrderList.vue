<template>
  <div class="page-card order-page">
    <header class="toolbar">
      <div>
        <h2 class="page-title">采购订单</h2>
        <p class="text-sub">P7｜M3 订单管理：草稿维护、会签审批、变更留痕与履约跟踪</p>
      </div>
      <div class="actions">
        <el-button v-if="store.hasPerm('m3:order:create')" type="primary" :icon="Plus" @click="openCreate">
          新建订单
        </el-button>
        <el-button
          v-if="store.hasPerm('m3:order:create')"
          :icon="Switch"
          @click="openFromRequest"
        >
          由申请单生成
        </el-button>
        <el-button
          v-if="store.hasPerm('m3:delivery:summary')"
          :icon="DataLine"
          @click="openSummary"
        >
          交付汇总
        </el-button>
      </div>
    </header>

    <el-form :inline="true" class="filter-bar" @submit.prevent>
      <el-form-item label="订单号">
        <el-input
          v-model="query.orderNo"
          placeholder="订单号模糊匹配"
          clearable
          style="width: 200px"
          @keyup.enter="reload(1)"
          @clear="reload(1)"
        />
      </el-form-item>
      <el-form-item label="生命周期">
        <el-select v-model="query.lifecycleStatus" placeholder="全部" clearable style="width: 110px">
          <el-option label="草稿" :value="1" />
          <el-option label="已提交" :value="2" />
          <el-option label="已审核" :value="3" />
          <el-option label="已关闭" :value="4" />
          <el-option label="已作废" :value="5" />
        </el-select>
      </el-form-item>
      <el-form-item label="审核">
        <el-select v-model="query.auditStatus" placeholder="全部" clearable style="width: 110px">
          <el-option label="未审核" :value="1" />
          <el-option label="已通过" :value="2" />
          <el-option label="已驳回" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item label="履约">
        <el-select v-model="query.fulfillStatus" placeholder="全部" clearable style="width: 110px">
          <el-option label="待交付" :value="1" />
          <el-option label="部分交付" :value="2" />
          <el-option label="全部交付" :value="3" />
          <el-option label="已关闭" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="偏离">
        <el-select v-model="query.deviationLevel" placeholder="全部" clearable style="width: 110px">
          <el-option label="关注" :value="1" />
          <el-option label="显著" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="下单日期">
        <el-date-picker
          v-model="query.dateRange"
          type="daterange"
          range-separator="~"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="query.overdue" @change="reload(1)">仅看超期未交付</el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="reload(1)">查询</el-button>
        <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      class="scope-alert"
      type="info"
      :closable="false"
      show-icon
      title="列表按数据范围过滤：采购员只见本部门订单，主管可见被授权部门；超期天数为服务端按交货期与当前日期计算（M3-5-4）。"
    />

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="orderNo" label="订单号" width="185" fixed="left" />
      <el-table-column prop="orderDate" label="下单日期" width="120" />
      <el-table-column prop="supplierName" label="供应商" min-width="160" show-overflow-tooltip />
      <el-table-column prop="buyerName" label="采购员" width="90" />
      <el-table-column prop="deptName" label="部门" width="90" />
      <el-table-column label="交货期" width="130">
        <template #default="{ row }">
          <span>{{ row.deliveryDate || '—' }}</span>
          <el-tag v-if="row.overdueDays > 0" type="danger" size="small" class="overdue-tag">
            超期 {{ row.overdueDays }} 天
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="100" align="right">
        <template #default="{ row }">{{ row.totalQty }}</template>
      </el-table-column>
      <el-table-column label="含税金额" width="120" align="right">
        <template #default="{ row }">
          <span>¥{{ formatAmount(row.totalAmount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生命周期" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="lifecycleTagType(row.lifecycleStatus)" size="small">
            {{ row.lifecycleStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="审核" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="auditTagType(row.auditStatus)" size="small">
            {{ row.auditStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="履约" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="fulfillTagType(row.fulfillStatus)" size="small">
            {{ row.fulfillStatusName }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="偏离" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.deviationFlag > 0" :type="deviationTagType(row.deviationFlag)" size="small">
            {{ deviationName(row.deviationFlag) }}
          </el-tag>
          <span v-else class="text-sub">正常</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="410" fixed="right">
        <template #default="{ row }">
          <el-button v-if="store.hasPerm('m6:evidence:view')" link type="primary" @click="openEvidence(row)">
            价格证据
          </el-button>
          <el-button v-if="store.hasPerm('m3:order:view')" link type="primary" @click="openDetail(row)">
            详情
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:update') && row.lifecycleStatus === 1"
            link
            type="primary"
            @click="openEdit(row)"
          >
            维护明细
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:submit') && row.lifecycleStatus === 1"
            link
            type="success"
            @click="submitBill(row)"
          >
            提交
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:withdraw') && row.lifecycleStatus === 2"
            link
            type="warning"
            @click="withdrawBill(row)"
          >
            撤回
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order_change:create') && row.lifecycleStatus === 3"
            link
            type="primary"
            @click="openChange(row)"
          >
            变更登记
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:delivery:register') && (row.lifecycleStatus === 3 || row.lifecycleStatus === 4) && row.items.some((i) => i.remainingQty > 0 && i.itemFulfillStatus !== 4)"
            link
            type="success"
            @click="openDelivery(row)"
          >
            交付登记
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order_item:close') && row.lifecycleStatus === 3 && row.items.some((i) => i.itemFulfillStatus !== 4)"
            link
            type="warning"
            @click="openCloseItems(row)"
          >
            按行关闭
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:close') && row.lifecycleStatus === 3"
            link
            type="danger"
            @click="closeBill(row)"
          >
            关闭
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:void') && (row.lifecycleStatus === 2 || row.lifecycleStatus === 3)"
            link
            type="danger"
            @click="voidBill(row)"
          >
            作废
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:reaudit') && row.lifecycleStatus === 3"
            link
            type="danger"
            @click="openReaudit(row)"
          >
            反审
          </el-button>
          <el-button
            v-if="store.hasPerm('m3:order:export')"
            link
            type="primary"
            @click="downloadOrderCsv(row.id)"
          >
            导出
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, sizes, prev, pager, next"
      :total="total"
      :current-page="query.pageNo"
      :page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="reload"
      @size-change="onSizeChange"
    />

    <!-- 详情抽屉（P8）：明细 / 审批记录 / 变更历史 / 交付流水 + 溯源 -->
    <el-drawer v-model="detailVisible" title="采购订单详情" size="860px">
      <template v-if="detail">
        <div class="detail-head">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="下单日期">{{ detail.orderDate }}</el-descriptions-item>
            <el-descriptions-item label="供应商">{{ detail.supplierName }}</el-descriptions-item>
            <el-descriptions-item label="采购员">{{ detail.buyerName }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ detail.deptName }}</el-descriptions-item>
            <el-descriptions-item label="结算方式">{{ detail.settlementTypeName }}</el-descriptions-item>
            <el-descriptions-item label="交货期">
              {{ detail.deliveryDate || '—' }}
              <el-tag v-if="detail.overdueDays > 0" type="danger" size="small">
                超期 {{ detail.overdueDays }} 天
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="交货地点" :span="2">{{ detail.deliveryPlace || '—' }}</el-descriptions-item>
            <el-descriptions-item label="生命周期">
              <el-tag :type="lifecycleTagType(detail.lifecycleStatus)" size="small">
                {{ detail.lifecycleStatusName }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="审核状态">
              <el-tag :type="auditTagType(detail.auditStatus)" size="small">
                {{ detail.auditStatusName }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="履约状态">
              <el-tag :type="fulfillTagType(detail.fulfillStatus)" size="small">
                {{ detail.fulfillStatusName }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="价格偏离">
              <el-tag v-if="detail.deviationFlag > 0" :type="deviationTagType(detail.deviationFlag)" size="small">
                {{ deviationName(detail.deviationFlag) }}
              </el-tag>
              <span v-else class="text-sub">正常</span>
            </el-descriptions-item>
            <el-descriptions-item label="含税金额">¥{{ formatAmount(detail.totalAmount) }}</el-descriptions-item>
            <el-descriptions-item label="不含税金额">¥{{ formatAmount(detail.noTaxAmount) }}</el-descriptions-item>
            <el-descriptions-item label="税额">¥{{ formatAmount(detail.taxAmount) }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.sourceRequestNo" label="来源申请单">
              {{ detail.sourceRequestNo }}
            </el-descriptions-item>
            <el-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="2">
              {{ detail.closeReason }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">{{ detail.remark || '—' }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions">
            <el-button
              v-if="store.hasPerm('m3:order:trace')"
              size="small"
              :icon="Connection"
              @click="openTrace(detail)"
            >
              溯源来源申请单
            </el-button>
          </div>
        </div>

        <el-tabs v-model="detailTab">
          <el-tab-pane label="订单明细" name="items">
            <el-table :data="detail.items" border size="small">
              <el-table-column prop="itemNo" label="#" width="44" />
              <el-table-column prop="materialNo" label="物料编码" width="120" />
              <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
              <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
              <el-table-column label="数量" width="95" align="right">
                <template #default="{ row }">{{ row.quantity }} {{ row.unitName }}</template>
              </el-table-column>
              <el-table-column label="含税单价" width="95" align="right">
                <template #default="{ row }">¥{{ formatAmount(row.unitPrice) }}</template>
              </el-table-column>
              <el-table-column label="税率" width="70" align="center">
                <template #default="{ row }">{{ (row.taxRate * 100).toFixed(0) }}%</template>
              </el-table-column>
              <el-table-column label="金额" width="105" align="right">
                <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
              </el-table-column>
              <el-table-column label="已交付" width="90" align="right">
                <template #default="{ row }">{{ row.deliveredQty }}</template>
              </el-table-column>
              <el-table-column label="剩余" width="90" align="right">
                <template #default="{ row }">{{ row.remainingQty }}</template>
              </el-table-column>
              <el-table-column label="归属对象" min-width="120" show-overflow-tooltip>
                <template #default="{ row }">{{ row.ownerName || '—' }}</template>
              </el-table-column>
              <el-table-column label="行状态" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="itemFulfillTagType(row.itemFulfillStatus)" size="small">
                    {{ row.itemFulfillStatusName }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="偏离率" width="100" align="right">
                <template #default="{ row }">
                  <span
                    v-if="row.deviationRate !== null"
                    :class="`dev-${row.deviationLevel === 2 ? 'significant' : row.deviationLevel === 1 ? 'focus' : 'normal'}`"
                  >
                    {{ (row.deviationRate * 100).toFixed(2) }}%
                  </span>
                  <span v-else class="text-sub">未判定</span>
                </template>
              </el-table-column>
              <el-table-column label="偏离等级" width="90" align="center">
                <template #default="{ row }">
                  <el-tag
                    v-if="row.deviationLevel > 0"
                    :type="row.deviationLevel === 2 ? 'danger' : 'warning'"
                    size="small"
                  >
                    {{ row.deviationLevelName || (row.deviationLevel === 2 ? '显著' : '关注') }}
                  </el-tag>
                  <el-tag v-else-if="row.deviationRate !== null" type="success" size="small">正常</el-tag>
                  <span v-else class="text-sub">—</span>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="审批记录" name="approval">
            <el-timeline v-if="detail.approvalHistory.length">
              <el-timeline-item
                v-for="h in detail.approvalHistory"
                :key="h.taskId"
                :type="historyNodeType(h.taskStatus)"
                :timestamp="formatTime(h.handleTime)"
                placement="top"
              >
                <div class="history-item">
                  <strong>节点 {{ h.nodeSeq }}｜{{ h.nodeName }}</strong>
                  <span class="history-meta">{{ h.signTypeName }}｜{{ h.approverName }}</span>
                  <el-tag :type="historyNodeType(h.taskStatus)" size="small">{{ h.taskStatusName }}</el-tag>
                  <p v-if="h.opinion" class="history-opinion">意见：{{ h.opinion }}</p>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无审批记录" :image-size="60" />
          </el-tab-pane>

          <el-tab-pane label="变更历史" name="change">
            <el-table v-if="detail.changeLogs.length" :data="detail.changeLogs" border size="small">
              <el-table-column prop="changeAt" label="时间" width="160">
                <template #default="{ row }">{{ formatTime(row.changeAt) }}</template>
              </el-table-column>
              <el-table-column label="行号" width="60" align="center">
                <template #default="{ row }">{{ row.itemNo ?? '整单' }}</template>
              </el-table-column>
              <el-table-column prop="changeFieldName" label="字段" width="100" />
              <el-table-column label="变更值" min-width="200">
                <template #default="{ row }">
                  <span class="change-before">{{ row.beforeValue || '（空）' }}</span>
                  <el-icon class="change-arrow"><Right /></el-icon>
                  <span class="change-after">{{ row.afterValue || '（空）' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="changeReason" label="原因" min-width="140" show-overflow-tooltip />
              <el-table-column prop="operatorName" label="操作人" width="90" />
            </el-table>
            <el-empty v-else description="暂无变更记录" :image-size="60" />
          </el-tab-pane>

          <el-tab-pane label="交付流水" name="delivery">
            <el-table v-if="detail.deliveryRecords.length" :data="detail.deliveryRecords" border size="small">
              <el-table-column prop="createdAt" label="登记时间" width="160">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="行号" width="60" align="center">
                <template #default="{ row }">{{ row.itemNo }}</template>
              </el-table-column>
              <el-table-column prop="materialName" label="物料" min-width="120" show-overflow-tooltip />
              <el-table-column label="交付数量" width="100" align="right">
                <template #default="{ row }">{{ row.deliveryQty }}</template>
              </el-table-column>
              <el-table-column prop="deliveryDate" label="交货日期" width="120" />
              <el-table-column prop="batchNo" label="批次" width="140" show-overflow-tooltip />
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
              <el-table-column prop="operatorName" label="登记人" width="90" />
            </el-table>
            <el-empty v-else description="暂无交付登记" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 溯源：来源申请单详情（M3-1-1） -->
    <el-drawer v-model="traceVisible" title="订单溯源：来源申请单" size="720px">
      <template v-if="traceBill">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="申请单号">{{ traceBill.requestNo }}</el-descriptions-item>
          <el-descriptions-item label="申请日期">{{ traceBill.requestDate }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ traceBill.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ traceBill.deptName }}</el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ traceBill.requireDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="合计金额">¥{{ formatAmount(traceBill.totalAmount) }}</el-descriptions-item>
        </el-descriptions>
        <h4 class="block-title">申请明细</h4>
        <el-table :data="traceBill.items" border size="small">
          <el-table-column prop="itemNo" label="#" width="44" />
          <el-table-column prop="materialNo" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ row.quantity }} {{ row.unitName }}</template>
          </el-table-column>
          <el-table-column label="归属对象" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.ownerName || '—' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 新建订单：主表 + 明细行编辑器（M3-1-2 / M3-2-1） -->
    <el-dialog v-model="createVisible" title="新建采购订单" width="1020px" @close="resetCreateForm">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="先建草稿单，再逐行保存明细；明细行的物料必须在所选供应商的供货范围内（M3-2-3）。"
        class="dialog-alert"
      />
      <el-form :model="createForm" label-width="90px" class="create-head">
        <el-form-item label="供应商" required>
          <el-select
            v-model="createForm.supplierId"
            placeholder="选择供应商"
            filterable
            style="width: 320px"
          >
            <el-option
              v-for="s in supplierSelectOptions"
              :key="s.id"
              :label="`${s.supplierNo} ${s.supplierName}`"
              :value="s.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="下单日期">
          <el-date-picker
            v-model="createForm.orderDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="交货期">
          <el-date-picker
            v-model="createForm.deliveryDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="交货地点">
          <el-input v-model="createForm.deliveryPlace" maxlength="100" style="width: 320px" />
        </el-form-item>
        <el-form-item label="结算方式">
          <el-select v-model="createForm.settlementType" style="width: 160px">
            <el-option label="月结" :value="1" />
            <el-option label="货到付款" :value="2" />
            <el-option label="预付款" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" maxlength="200" style="width: 480px" />
        </el-form-item>
      </el-form>

      <h4 class="block-title">
        订单明细
        <el-button class="add-row-btn" :icon="Plus" size="small" @click="addDraftRow">添加一行</el-button>
      </h4>
      <el-table :data="createForm.items" border size="small" empty-text="尚未添加明细行">
        <el-table-column label="物料" min-width="200">
          <template #default="{ row }">
            <el-select
              v-model="row.materialId"
              placeholder="选择物料"
              filterable
              size="small"
              style="width: 100%"
              @change="onDraftMaterialChange(row)"
            >
              <el-option
                v-for="m in materialSelectOptions"
                :key="m.id"
                :label="`${m.materialNo} ${m.materialName}`"
                :value="m.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="105">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0.01" :precision="2" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="含税单价" width="105">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" size="small" controls-position="right" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="税率" width="105">
          <template #default="{ row }">
            <el-select v-model="row.taxRate" size="small" style="width: 100%">
              <el-option label="13%" :value="0.13" />
              <el-option label="9%" :value="0.09" />
              <el-option label="6%" :value="0.06" />
              <el-option label="0%" :value="0" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="交货期" width="140">
          <template #default="{ row }">
            <el-date-picker v-model="row.deliveryDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="归属对象" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row.ownerId" placeholder="部门领用（可不选）" clearable filterable size="small" style="width: 100%">
              <el-option
                v-for="o in ownerSelectOptions"
                :key="o.id"
                :label="`${o.ownerTypeName}｜${o.ownerName}`"
                :value="o.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            ¥{{ formatAmount((row.quantity || 0) * (row.unitPrice || 0)) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" :icon="Delete" @click="createForm.items.splice($index, 1)" />
          </template>
        </el-table-column>
      </el-table>
      <div class="dialog-total">合计：¥{{ formatAmount(createTotal) }}</div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 维护明细：草稿单的主表与明细行增删改（M3-1-4 / M3-2-2） -->
    <el-dialog v-model="editVisible" title="维护订单明细" width="1020px">
      <template v-if="editBill">
        <el-descriptions :column="3" border size="small" class="edit-head">
          <el-descriptions-item label="订单号">{{ editBill.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="采购员">{{ editBill.buyerName }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ editBill.deptName }}</el-descriptions-item>
        </el-descriptions>
        <el-form :model="editForm" label-width="90px" class="edit-main">
          <el-form-item label="供应商" required>
            <el-select v-model="editForm.supplierId" placeholder="选择供应商" filterable style="width: 320px">
              <el-option
                v-for="s in supplierSelectOptions"
                :key="s.id"
                :label="`${s.supplierNo} ${s.supplierName}`"
                :value="s.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="交货期">
            <el-date-picker v-model="editForm.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 160px" />
          </el-form-item>
          <el-form-item label="交货地点">
            <el-input v-model="editForm.deliveryPlace" maxlength="100" style="width: 320px" />
          </el-form-item>
          <el-form-item label="结算方式">
            <el-select v-model="editForm.settlementType" style="width: 160px">
              <el-option label="月结" :value="1" />
              <el-option label="货到付款" :value="2" />
              <el-option label="预付款" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="200" style="width: 480px" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" type="primary" :loading="saving" @click="saveEditMain">保存主表</el-button>
          </el-form-item>
        </el-form>
        <div class="edit-actions">
          <el-button size="small" :icon="Plus" @click="addEditRow">添加一行</el-button>
        </div>
        <el-table :data="editBill.items" border size="small">
          <el-table-column prop="itemNo" label="#" width="44" />
          <el-table-column prop="materialNo" label="物料编码" width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="数量" width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.quantity"
                :min="0.01"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 100%"
                @change="saveEditRow(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="含税单价" width="110">
            <template #default="{ row }">
              <el-input-number
                v-model="row.unitPrice"
                :min="0"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 100%"
                @change="saveEditRow(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="交货期" width="140">
            <template #default="{ row }">
              <el-date-picker
                v-model="row.deliveryDate"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                style="width: 100%"
                @change="saveEditRow(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="归属对象" min-width="160">
            <template #default="{ row }">
              <el-select
                v-model="row.ownerId"
                placeholder="部门领用"
                clearable
                filterable
                size="small"
                style="width: 100%"
                @change="saveEditRow(row)"
              >
                <el-option
                  v-for="o in ownerSelectOptions"
                  :key="o.id"
                  :label="`${o.ownerTypeName}｜${o.ownerName}`"
                  :value="o.id"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template #default="{ row }">
              <el-button link type="danger" :icon="Delete" @click="deleteEditRow(row)" />
            </template>
          </el-table-column>
        </el-table>
        <div class="dialog-total">合计：¥{{ formatAmount(editBill.totalAmount) }}</div>
      </template>
    </el-dialog>

    <!-- 由申请单生成（M3-1-3） -->
    <el-dialog v-model="fromRequestVisible" title="由申请单生成订单" width="760px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅已审核且指定了建议供应商的申请单可转订单；生成的订单为草稿，明细与归属对象随申请单带入（M3-1-3）。"
        class="dialog-alert"
      />
      <el-table
        v-loading="fromLoading"
        :data="approvedRequests"
        border
        size="small"
        highlight-current-row
        @current-change="onPickRequest"
      >
        <el-table-column prop="requestNo" label="申请单号" width="185" />
        <el-table-column prop="requestDate" label="申请日期" width="120" />
        <el-table-column prop="applicantName" label="申请人" width="90" />
        <el-table-column prop="deptName" label="部门" width="90" />
        <el-table-column label="合计金额" width="120" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="明细" width="70" align="center">
          <template #default="{ row }">{{ row.items.length }} 行</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="fromRequestVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!pickedRequest" @click="confirmFromRequest">
          生成订单
        </el-button>
      </template>
    </el-dialog>

    <!-- 变更登记（M3-4-1）：行级 / 整单级，前后值留痕 -->
    <el-dialog v-model="changeVisible" title="订单变更登记" width="640px">
      <template v-if="changeBill">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="变更登记用于已审核订单的数量、单价、交期、地点与结算方式调整；系统逐字段比对前后值写入变更历史（M3-4-1）。"
          class="dialog-alert"
        />
        <el-form label-width="90px">
          <el-form-item label="变更范围">
            <el-radio-group v-model="changeForm.itemId">
              <el-radio :value="null">整单级</el-radio>
              <el-radio
                v-for="item in changeBill.items"
                :key="item.id"
                :value="item.id"
              >
                行 {{ item.itemNo }}｜{{ item.materialName }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="changeForm.itemId">
            <el-form-item label="数量">
              <el-input-number v-model="changeForm.quantity" :min="0.01" :precision="2" controls-position="right" />
            </el-form-item>
            <el-form-item label="含税单价">
              <el-input-number v-model="changeForm.unitPrice" :min="0" :precision="2" controls-position="right" />
            </el-form-item>
          </template>
          <el-form-item label="交货期">
            <el-date-picker
              v-model="changeForm.deliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="保持不变"
            />
          </el-form-item>
          <el-form-item v-if="!changeForm.itemId" label="交货地点">
            <el-input v-model="changeForm.deliveryPlace" maxlength="100" placeholder="保持不变" />
          </el-form-item>
          <el-form-item v-if="!changeForm.itemId" label="结算方式">
            <el-select v-model="changeForm.settlementType" placeholder="保持不变" clearable>
              <el-option label="月结" :value="1" />
              <el-option label="货到付款" :value="2" />
              <el-option label="预付款" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="变更原因" required>
            <el-input
              v-model="changeForm.reason"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="请填写变更原因，将随前后值一并留痕"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="changeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmChange">登记变更</el-button>
      </template>
    </el-dialog>

    <!-- 交付登记（M3-5-1）：累计不超过订单行数量 -->
    <el-dialog v-model="deliveryVisible" title="交付进度登记" width="600px">
      <template v-if="deliveryBill">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="交付登记只写履约流水；已交付数量由入库单确认回写（M3-5-1，库存唯一写入点为入库确认）。"
          class="dialog-alert"
        />
        <el-form label-width="90px">
          <el-form-item label="明细行" required>
            <el-select v-model="deliveryForm.orderItemId" placeholder="选择明细行" style="width: 100%">
              <el-option
                v-for="item in deliveryBill.items.filter((i) => i.remainingQty > 0 && i.itemFulfillStatus !== 4)"
                :key="item.id"
                :label="`行 ${item.itemNo}｜${item.materialName}｜剩余 ${item.remainingQty}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="交付数量" required>
            <el-input-number
              v-model="deliveryForm.deliveryQty"
              :min="0.01"
              :max="selectedItemRemaining"
              :precision="2"
              controls-position="right"
              style="width: 220px"
            />
            <span class="form-hint">剩余可登记 {{ selectedItemRemaining }}</span>
          </el-form-item>
          <el-form-item label="交货日期">
            <el-date-picker v-model="deliveryForm.deliveryDate" type="date" value-format="YYYY-MM-DD" style="width: 180px" />
          </el-form-item>
          <el-form-item label="批次号">
            <el-input v-model="deliveryForm.batchNo" maxlength="50" style="width: 220px" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="deliveryForm.remark" maxlength="200" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="deliveryVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmDelivery">登记交付</el-button>
      </template>
    </el-dialog>

    <!-- 按行关闭（M3-2-4） -->
    <el-dialog v-model="closeItemsVisible" title="按行关闭" width="680px">
      <template v-if="closeItemsBill">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="勾选的明细行剩余数量不再要求交付，行履约状态置已关闭；全部行关闭后主单履约状态重算为已关闭（M3-2-4）。"
          class="dialog-alert"
        />
        <el-table
          :data="closeItemsBill.items.filter((i) => i.itemFulfillStatus !== 4)"
          border
          size="small"
          @selection-change="(rows: OrderItemRow[]) => (closeItemIds = rows.map((r) => r.id))"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="itemNo" label="#" width="44" />
          <el-table-column prop="materialName" label="物料" min-width="140" show-overflow-tooltip />
          <el-table-column label="数量" width="100" align="right">
            <template #default="{ row }">{{ row.quantity }} {{ row.unitName }}</template>
          </el-table-column>
          <el-table-column label="剩余" width="100" align="right">
            <template #default="{ row }">{{ row.remainingQty }}</template>
          </el-table-column>
          <el-table-column label="行状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="itemFulfillTagType(row.itemFulfillStatus)" size="small">
                {{ row.itemFulfillStatusName }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="closeItemsVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" :disabled="!closeItemIds.length" @click="confirmCloseItems">
          关闭勾选行
        </el-button>
      </template>
    </el-dialog>

    <!-- 整单关闭（M3-3-6）：原因必填 -->
    <el-dialog v-model="closeVisible" title="关闭订单" width="520px">
      <el-form label-width="70px">
        <el-form-item label="关闭原因" required>
          <el-input
            v-model="closeReason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写关闭原因，剩余未交付部分将终止"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="confirmClose">确认关闭</el-button>
      </template>
    </el-dialog>

    <!-- 反审（M3-3-5）：仅系统管理员，需二次确认 -->
    <el-dialog v-model="reauditVisible" title="反审（撤销审核）" width="560px">
      <el-alert
        type="error"
        :closable="false"
        show-icon
        title="反审仅系统管理员可操作，且订单未发生任何到货事实（无交付登记、无到货验收、无入库记录）。反审后订单回到已提交态，原审批历史保留，可重新提交走审批。"
        class="dialog-alert"
      />
      <el-form label-width="70px">
        <el-form-item label="反审原因" required>
          <el-input
            v-model="reauditReason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="请填写反审原因，操作将记入审计日志"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reauditVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="confirmReaudit">确认反审</el-button>
      </template>
    </el-dialog>

    <!-- 交付汇总（M3-5-5） -->
    <el-dialog v-model="summaryVisible" title="交付汇总" width="900px">
      <div class="summary-filter">
        <el-date-picker
          v-model="summaryRange"
          type="daterange"
          range-separator="~"
          start-placeholder="开始日期"
          end-placeholder="开始日期"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
        <el-button type="primary" :icon="Search" @click="loadSummary">查询</el-button>
      </div>
      <el-table v-loading="summaryLoading" :data="summaryRows" border size="small">
        <el-table-column prop="supplierName" label="供应商" min-width="180" show-overflow-tooltip />
        <el-table-column label="订单数" width="80" align="center">
          <template #default="{ row }">{{ row.orderCount }}</template>
        </el-table-column>
        <el-table-column label="订单数量" width="100" align="right">
          <template #default="{ row }">{{ row.totalQty }}</template>
        </el-table-column>
        <el-table-column label="已交付" width="100" align="right">
          <template #default="{ row }">{{ row.deliveredQty }}</template>
        </el-table-column>
        <el-table-column label="未交付" width="100" align="right">
          <template #default="{ row }">{{ row.remainingQty }}</template>
        </el-table-column>
        <el-table-column label="订单金额" width="120" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="已交付金额" width="120" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.deliveredAmount) }}</template>
        </el-table-column>
        <el-table-column label="未交付金额" width="120" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.remainingAmount) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="summaryRows.length" class="dialog-total">
        合计：{{ summaryTotals.orderCount }} 单｜已交付金额 ¥{{ formatAmount(summaryTotals.deliveredAmount) }}｜未交付金额 ¥{{ formatAmount(summaryTotals.remainingAmount) }}
      </div>
      <el-empty v-if="!summaryLoading && !summaryRows.length" description="日期范围内暂无已审核订单" :image-size="60" />
    </el-dialog>
    <!-- 价格证据面板（M6-4-1）：审批辅助一屏展示单价/参考价/偏离/历史成交 -->
    <el-drawer v-model="evidenceVisible" title="价格证据（审批辅助）" size="860px">
      <template v-if="evidence">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="订单号">{{ evidence.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ evidence.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="整单偏离">
            <el-tag v-if="evidence.deviationFlag > 0" :type="deviationTagType(evidence.deviationFlag)" size="small">
              {{ deviationName(evidence.deviationFlag) }}
            </el-tag>
            <span v-else>正常</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table v-loading="evidenceLoading" :data="evidence.rows" border stripe>
          <el-table-column prop="materialNo" label="物料编码" width="110" />
          <el-table-column prop="materialName" label="物料名称" min-width="130" show-overflow-tooltip />
          <el-table-column label="订单单价" width="90" align="right">
            <template #default="{ row }">{{ row.unitPrice }}</template>
          </el-table-column>
          <el-table-column label="下单时点参考价" width="110" align="right">
            <template #default="{ row }">
              <span v-if="row.refPrice !== null">{{ row.refPrice }}</span>
              <span v-else class="text-sub">未设定</span>
            </template>
          </el-table-column>
          <el-table-column label="偏离率" width="100" align="right">
            <template #default="{ row }">
              <span v-if="row.deviationRate !== null" :class="`dev-${row.deviationLevel === 2 ? 'significant' : row.deviationLevel === 1 ? 'focus' : 'normal'}`">
                {{ (row.deviationRate * 100).toFixed(2) }}%
              </span>
              <span v-else class="text-sub">未判定</span>
            </template>
          </el-table-column>
          <el-table-column label="等级" width="70" align="center">
            <template #default="{ row }">
              <el-tag :type="row.deviationLevel === 2 ? 'danger' : row.deviationLevel === 1 ? 'warning' : 'success'" size="small">
                {{ row.deviationLevelName }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="历史均价" width="90" align="right">
            <template #default="{ row }">
              <span v-if="row.avgPrice !== null">{{ row.avgPrice }}</span>
              <span v-else class="text-sub">无样本</span>
            </template>
          </el-table-column>
          <el-table-column label="最近成交价" width="100" align="right">
            <template #default="{ row }">
              <span v-if="row.lastPrice !== null">{{ row.lastPrice }}</span>
              <span v-else class="text-sub">无样本</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastBillNo" label="来源单号" width="170" show-overflow-tooltip />
          <el-table-column label="证据缺口" min-width="140">
            <template #default="{ row }">
              <span v-if="row.note" class="text-warning">{{ row.note }}</span>
              <span v-else class="text-sub">—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-alert class="scope-alert" type="info" :closable="false" show-icon
          title="参考价为下单时点（订单创建日）选用的版本，历史版本停用后仍可还原；偏离率为最近一次保存或重算时的判定；历史成交来自统计快照（刷新后更新）。" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, DataLine, Delete, Plus, Refresh, Right, Search, Switch } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import * as m3Api from '@/api/m3'
import type { DeliverySummaryRow, OrderItemRow, OrderRow } from '@/api/m3'
import * as m2Api from '@/api/m2'
import type { RequestRow } from '@/api/m2'
import { materialOptions, supplierOptions } from '@/api/m1'
import type { MaterialRow, SupplierRow } from '@/api/m1'
import * as m6Api from '@/api/m6'
import type { EvidenceOrder } from '@/api/m6'

interface MaterialOption {
  id: number
  materialNo: string
  materialName: string
  unitName: string
}

interface SupplierOption {
  id: number
  supplierNo: string
  supplierName: string
}

interface OwnerOption {
  id: number
  ownerType: number
  ownerTypeName: string
  ownerName: string
}

interface DraftItem {
  materialId?: number
  quantity: number
  unitPrice: number
  taxRate: number
  deliveryDate: string
  ownerType: number
  ownerId?: number
}

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const rows = ref<OrderRow[]>([])
const total = ref(0)

const query = reactive({
  orderNo: '',
  lifecycleStatus: undefined as number | undefined,
  auditStatus: undefined as number | undefined,
  fulfillStatus: undefined as number | undefined,
  deviationLevel: undefined as number | undefined,
  dateRange: [] as string[],
  overdue: false,
  pageNo: 1,
  pageSize: 10
})

// ---------------- 选项数据 ----------------

const materialSelectOptions = ref<MaterialOption[]>([])
const supplierSelectOptions = ref<SupplierOption[]>([])
const ownerSelectOptions = ref<OwnerOption[]>([])

async function loadOptions() {
  // 归属对象下拉挂 m3:order_item:owner（采购员口径）：主管等无该权限的角色
  // 打开订单页时不发请求，避免 403 冒泡成 pageerror 与错误 toast（同 M2 修法）
  const [materials, suppliers, owners] = await Promise.all([
    materialOptions() as Promise<MaterialRow[]>,
    supplierOptions() as Promise<SupplierRow[]>,
    store.hasPerm('m3:order_item:owner')
      ? m3Api.ownerOptions()
      : Promise.resolve({ options: [] as { id: number; ownerType: number; ownerTypeName: string; ownerNo: string; ownerName: string }[] })
  ])
  materialSelectOptions.value = materials.map((m) => ({
    id: m.id,
    materialNo: m.materialNo,
    materialName: m.materialName,
    unitName: m.unitName
  }))
  supplierSelectOptions.value = suppliers.map((s) => ({
    id: s.id,
    supplierNo: s.supplierNo,
    supplierName: s.supplierName
  }))
  ownerSelectOptions.value = owners.options.map((o) => ({
    id: o.id,
    ownerType: o.ownerType,
    ownerTypeName: o.ownerTypeName,
    ownerName: o.ownerName
  }))
}

// ---------------- 列表 ----------------

async function load() {
  loading.value = true
  try {
    const params: Record<string, unknown> = {
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      orderNo: query.orderNo || undefined,
      lifecycleStatus: query.lifecycleStatus,
      auditStatus: query.auditStatus,
      fulfillStatus: query.fulfillStatus,
      deviationLevel: query.deviationLevel,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1],
      overdue: query.overdue ? true : undefined
    }
    const result = await m3Api.pageOrders(params)
    rows.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function reload(pageNo = query.pageNo) {
  query.pageNo = pageNo
  load()
}

function onSizeChange(size: number) {
  query.pageSize = size
  reload(1)
}

function resetQuery() {
  query.orderNo = ''
  query.lifecycleStatus = undefined
  query.auditStatus = undefined
  query.fulfillStatus = undefined
  query.deviationLevel = undefined
  query.dateRange = []
  query.overdue = false
  reload(1)
}

// ---------------- 展示辅助 ----------------

function formatAmount(value: number | undefined | null) {
  if (value == null) return '0.00'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatTime(value: string | null | undefined) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function lifecycleTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'warning'
  if (status === 3) return 'success'
  if (status === 4) return 'info'
  return 'danger'
}

function auditTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'success'
  return 'danger'
}

function fulfillTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'warning'
  if (status === 3) return 'success'
  return 'info'
}

function itemFulfillTagType(status: number) {
  if (status === 1) return 'info'
  if (status === 2) return 'warning'
  if (status === 3) return 'success'
  return 'info'
}

function historyNodeType(taskStatus: number) {
  if (taskStatus === 2) return 'success'
  if (taskStatus === 3) return 'danger'
  if (taskStatus === 4) return 'info'
  return 'warning'
}

// ---------------- 详情与溯源 ----------------

const detailVisible = ref(false)
const detail = ref<OrderRow | null>(null)
const detailTab = ref('items')

async function openDetail(row: OrderRow) {
  detail.value = await m3Api.getOrder(row.id)
  detailTab.value = 'items'
  detailVisible.value = true
}

const traceVisible = ref(false)
const traceBill = ref<RequestRow | null>(null)

async function openTrace(row: OrderRow) {
  try {
    traceBill.value = await m3Api.orderTrace(row.id)
    traceVisible.value = true
  } catch {
    // 手工创建订单无来源申请单，后端返回 NoData，拦截器已提示
  }
}

// ---------------- 新建 ----------------

const createVisible = ref(false)
const createForm = reactive({
  supplierId: undefined as number | undefined,
  orderDate: '',
  deliveryDate: '',
  deliveryPlace: '',
  settlementType: 1,
  remark: '',
  items: [] as DraftItem[]
})
const createTotal = computed(() =>
  createForm.items.reduce((sum, item) => sum + (item.quantity || 0) * (item.unitPrice || 0), 0)
)

function openCreate() {
  createForm.supplierId = undefined
  createForm.orderDate = ''
  createForm.deliveryDate = ''
  createForm.deliveryPlace = ''
  createForm.settlementType = 1
  createForm.remark = ''
  createForm.items = []
  addDraftRow()
  createVisible.value = true
}

function resetCreateForm() {
  createForm.items = []
}

function addDraftRow() {
  createForm.items.push({
    quantity: 1,
    unitPrice: 0,
    taxRate: 0.13,
    deliveryDate: '',
    ownerType: 1
  })
}

function onDraftMaterialChange(row: DraftItem) {
  row.ownerId = undefined
}

async function saveCreate() {
  if (!createForm.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  const valid = createForm.items.filter((item) => item.materialId && item.quantity > 0)
  if (!valid.length) {
    ElMessage.warning('请至少填写一行完整明细（物料与数量必填）')
    return
  }
  saving.value = true
  try {
    const orderId = await m3Api.createOrder({
      supplierId: createForm.supplierId,
      orderDate: createForm.orderDate || undefined,
      deliveryDate: createForm.deliveryDate || undefined,
      deliveryPlace: createForm.deliveryPlace || undefined,
      settlementType: createForm.settlementType,
      remark: createForm.remark || undefined
    })
    for (const item of valid) {
      const owner = ownerSelectOptions.value.find((o) => o.id === item.ownerId)
      await m3Api.addOrderItem(orderId, {
        materialId: item.materialId,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        taxRate: item.taxRate,
        deliveryDate: item.deliveryDate || undefined,
        ownerType: owner ? owner.ownerType : 1,
        ownerId: owner ? owner.id : undefined
      })
    }
    ElMessage.success(`订单已创建，共 ${valid.length} 行明细`)
    createVisible.value = false
    reload(1)
  } finally {
    saving.value = false
  }
}

// ---------------- 维护明细 ----------------

const editVisible = ref(false)
const editBill = ref<OrderRow | null>(null)
const editForm = reactive({
  supplierId: undefined as number | undefined,
  deliveryDate: '',
  deliveryPlace: '',
  settlementType: 1,
  remark: ''
})

async function openEdit(row: OrderRow) {
  editBill.value = await m3Api.getOrder(row.id)
  editForm.supplierId = editBill.value.supplierId
  editForm.deliveryDate = editBill.value.deliveryDate || ''
  editForm.deliveryPlace = editBill.value.deliveryPlace || ''
  editForm.settlementType = editBill.value.settlementType
  editForm.remark = editBill.value.remark || ''
  editVisible.value = true
}

async function refreshEditBill() {
  if (!editBill.value) return
  editBill.value = await m3Api.getOrder(editBill.value.id)
}

async function saveEditMain() {
  if (!editBill.value) return
  if (!editForm.supplierId) {
    ElMessage.warning('请选择供应商')
    return
  }
  saving.value = true
  try {
    await m3Api.updateOrder(editBill.value.id, {
      supplierId: editForm.supplierId,
      deliveryDate: editForm.deliveryDate || undefined,
      deliveryPlace: editForm.deliveryPlace || undefined,
      settlementType: editForm.settlementType,
      remark: editForm.remark || undefined
    })
    await refreshEditBill()
    ElMessage.success('主表已保存')
    load()
  } finally {
    saving.value = false
  }
}

async function addEditRow() {
  const bill = editBill.value
  if (!bill) return
  const usedMaterials = new Set(bill.items.map((i) => i.materialId))
  const first = materialSelectOptions.value.find((m) => !usedMaterials.has(m.id))
  if (!first) {
    ElMessage.warning('没有可添加的物料了')
    return
  }
  saving.value = true
  try {
    await m3Api.addOrderItem(bill.id, {
      materialId: first.id,
      quantity: 1,
      unitPrice: 0,
      taxRate: 0.13,
      ownerType: 1
    })
    await refreshEditBill()
    ElMessage.success('已添加一行，请补充数量与单价')
  } finally {
    saving.value = false
  }
}

async function saveEditRow(row: OrderItemRow) {
  if (!editBill.value) return
  const owner = ownerSelectOptions.value.find((o) => o.id === row.ownerId)
  saving.value = true
  try {
    await m3Api.updateOrderItem(editBill.value.id, row.id, {
      materialId: row.materialId,
      quantity: row.quantity,
      unitPrice: row.unitPrice,
      taxRate: row.taxRate,
      deliveryDate: row.deliveryDate || undefined,
      ownerType: owner ? owner.ownerType : row.ownerType,
      ownerId: owner ? owner.id : undefined
    })
    await refreshEditBill()
  } finally {
    saving.value = false
  }
}

async function deleteEditRow(row: OrderItemRow) {
  if (!editBill.value) return
  await ElMessageBox.confirm(`确认删除明细行「${row.materialName}」？`, '删除确认', { type: 'warning' })
  await m3Api.deleteOrderItem(editBill.value.id, row.id)
  await refreshEditBill()
  ElMessage.success('明细行已删除')
  load()
}

// ---------------- 由申请单生成 ----------------

const fromRequestVisible = ref(false)
const fromLoading = ref(false)
const approvedRequests = ref<RequestRow[]>([])
const pickedRequest = ref<RequestRow | null>(null)

async function openFromRequest() {
  fromLoading.value = true
  pickedRequest.value = null
  try {
    const result = await m2Api.pageRequests({
      pageNo: 1,
      pageSize: 50,
      lifecycleStatus: 3,
      auditStatus: 2
    })
    approvedRequests.value = result.records
  } finally {
    fromLoading.value = false
  }
  fromRequestVisible.value = true
}

function onPickRequest(row: RequestRow | null) {
  pickedRequest.value = row
}

async function confirmFromRequest() {
  if (!pickedRequest.value) return
  saving.value = true
  try {
    const orderId = await m3Api.createOrderFromRequest(pickedRequest.value.id)
    fromRequestVisible.value = false
    ElMessage.success(`已生成订单（id ${orderId}），请维护明细后提交审批`)
    reload(1)
  } finally {
    saving.value = false
  }
}

// ---------------- 行内动作 ----------------

async function submitBill(row: OrderRow) {
  await ElMessageBox.confirm(`确认提交订单 ${row.orderNo} 进入会签审批？`, '提交确认', { type: 'info' })
  await m3Api.submitOrder(row.id)
  ElMessage.success('已提交审批')
  load()
}

async function withdrawBill(row: OrderRow) {
  await ElMessageBox.confirm('撤回后订单回到草稿，可修改后重新提交。确认撤回？', '撤回确认', { type: 'warning' })
  await m3Api.withdrawOrder(row.id)
  ElMessage.success('已撤回')
  load()
}

async function voidBill(row: OrderRow) {
  await ElMessageBox.confirm(
    `确认作废订单 ${row.orderNo}？作废后不可恢复，仅未发生到货事实的订单可作废。`,
    '作废确认',
    { type: 'warning' }
  )
  await m3Api.voidOrder(row.id)
  ElMessage.success('已作废')
  load()
}

// ---------------- 变更登记 ----------------

const changeVisible = ref(false)
const changeBill = ref<OrderRow | null>(null)
const changeForm = reactive({
  itemId: null as number | null,
  quantity: undefined as number | undefined,
  unitPrice: undefined as number | undefined,
  deliveryDate: '',
  deliveryPlace: '',
  settlementType: undefined as number | undefined,
  reason: ''
})

function openChange(row: OrderRow) {
  changeBill.value = row
  changeForm.itemId = null
  changeForm.quantity = undefined
  changeForm.unitPrice = undefined
  changeForm.deliveryDate = ''
  changeForm.deliveryPlace = ''
  changeForm.settlementType = undefined
  changeForm.reason = ''
  changeVisible.value = true
}

async function confirmChange() {
  if (!changeBill.value) return
  if (!changeForm.reason.trim()) {
    ElMessage.warning('请填写变更原因')
    return
  }
  saving.value = true
  try {
    await m3Api.changeOrder(changeBill.value.id, {
      itemId: changeForm.itemId,
      quantity: changeForm.quantity,
      unitPrice: changeForm.unitPrice,
      deliveryDate: changeForm.deliveryDate || undefined,
      deliveryPlace: changeForm.itemId ? undefined : changeForm.deliveryPlace || undefined,
      settlementType: changeForm.itemId ? undefined : changeForm.settlementType,
      reason: changeForm.reason.trim()
    })
    ElMessage.success('变更已登记，前后值已写入变更历史')
    changeVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 交付登记 ----------------

const deliveryVisible = ref(false)
const deliveryBill = ref<OrderRow | null>(null)
const deliveryForm = reactive({
  orderItemId: undefined as number | undefined,
  deliveryQty: 1,
  deliveryDate: '',
  batchNo: '',
  remark: ''
})

const selectedItemRemaining = computed(() => {
  const item = deliveryBill.value?.items.find((i) => i.id === deliveryForm.orderItemId)
  return item ? Number(item.remainingQty) : 0
})

function openDelivery(row: OrderRow) {
  deliveryBill.value = row
  deliveryForm.orderItemId = undefined
  deliveryForm.deliveryQty = 1
  deliveryForm.deliveryDate = ''
  deliveryForm.batchNo = ''
  deliveryForm.remark = ''
  deliveryVisible.value = true
}

async function confirmDelivery() {
  if (!deliveryBill.value) return
  if (!deliveryForm.orderItemId) {
    ElMessage.warning('请选择明细行')
    return
  }
  if (!deliveryForm.deliveryQty || deliveryForm.deliveryQty <= 0) {
    ElMessage.warning('请填写交付数量')
    return
  }
  saving.value = true
  try {
    await m3Api.registerDelivery(deliveryBill.value.id, {
      orderItemId: deliveryForm.orderItemId,
      deliveryQty: deliveryForm.deliveryQty,
      deliveryDate: deliveryForm.deliveryDate || undefined,
      batchNo: deliveryForm.batchNo || undefined,
      remark: deliveryForm.remark || undefined
    })
    ElMessage.success('交付已登记')
    deliveryVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 按行关闭 ----------------

const closeItemsVisible = ref(false)
const closeItemsBill = ref<OrderRow | null>(null)
const closeItemIds = ref<number[]>([])

function openCloseItems(row: OrderRow) {
  closeItemsBill.value = row
  closeItemIds.value = []
  closeItemsVisible.value = true
}

async function confirmCloseItems() {
  if (!closeItemsBill.value) return
  if (!closeItemIds.value.length) {
    ElMessage.warning('请先勾选要关闭的明细行')
    return
  }
  saving.value = true
  try {
    await m3Api.closeOrderItems(closeItemsBill.value.id, closeItemIds.value)
    ElMessage.success('勾选行已关闭')
    closeItemsVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 整单关闭 ----------------

const closeVisible = ref(false)
const closeReason = ref('')
const closingBill = ref<OrderRow | null>(null)

function closeBill(row: OrderRow) {
  closingBill.value = row
  closeReason.value = ''
  closeVisible.value = true
}

async function confirmClose() {
  if (!closingBill.value) return
  if (!closeReason.value.trim()) {
    ElMessage.warning('请填写关闭原因')
    return
  }
  saving.value = true
  try {
    await m3Api.closeOrder(closingBill.value.id, closeReason.value.trim())
    ElMessage.success('订单已关闭')
    closeVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 反审 ----------------

const reauditVisible = ref(false)
const reauditReason = ref('')
const reauditingBill = ref<OrderRow | null>(null)

function openReaudit(row: OrderRow) {
  reauditingBill.value = row
  reauditReason.value = ''
  reauditVisible.value = true
}

async function confirmReaudit() {
  if (!reauditingBill.value) return
  if (!reauditReason.value.trim()) {
    ElMessage.warning('请填写反审原因')
    return
  }
  await ElMessageBox.confirm(
    `反审为高危操作：订单 ${reauditingBill.value.orderNo} 将撤销审核结果回到已提交态，原审批历史保留。确认反审？`,
    '反审二次确认',
    { type: 'error' }
  )
  saving.value = true
  try {
    await m3Api.reauditOrder(reauditingBill.value.id, reauditReason.value.trim())
    ElMessage.success('已反审，订单回到已提交态')
    reauditVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// ---------------- 交付汇总 ----------------

const summaryVisible = ref(false)
const summaryLoading = ref(false)
const summaryRows = ref<DeliverySummaryRow[]>([])
const summaryRange = ref<string[]>([])

const summaryTotals = computed(() => ({
  orderCount: summaryRows.value.reduce((sum, r) => sum + (r.orderCount || 0), 0),
  deliveredAmount: summaryRows.value.reduce((sum, r) => sum + Number(r.deliveredAmount || 0), 0),
  remainingAmount: summaryRows.value.reduce((sum, r) => sum + Number(r.remainingAmount || 0), 0)
}))

function openSummary() {
  summaryRows.value = []
  summaryVisible.value = true
  loadSummary()
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    summaryRows.value = await m3Api.deliverySummary({
      startDate: summaryRange.value?.[0] || undefined,
      endDate: summaryRange.value?.[1] || undefined
    })
  } finally {
    summaryLoading.value = false
  }
}

// ---------------- 价格证据（M6-4-1） ----------------

const evidenceVisible = ref(false)
const evidenceLoading = ref(false)
const evidence = ref<EvidenceOrder | null>(null)

async function openEvidence(row: OrderRow) {
  evidenceLoading.value = true
  evidenceVisible.value = true
  try {
    evidence.value = await m6Api.getOrderEvidence(row.id)
  } finally {
    evidenceLoading.value = false
  }
}

function deviationName(flag: number) {
  return flag === 2 ? '显著' : '关注'
}

function deviationTagType(flag: number) {
  return flag === 2 ? 'danger' : 'warning'
}

onMounted(() => {  loadOptions()
  load()
})
</script>

<style scoped>
.scope-alert {
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.overdue-tag {
  margin-left: 6px;
}

.block-title {
  margin: 18px 0 10px;
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.add-row-btn {
  margin-left: 10px;
}

.dialog-alert {
  margin-bottom: 12px;
}

.dialog-total {
  margin-top: 10px;
  text-align: right;
  font-weight: 600;
}

/* M3-6 偏离等级配色（与 P16 偏离关注页一致） */
.dev-normal {
  color: var(--el-color-success);
}

.dev-focus {
  color: var(--el-color-warning);
  font-weight: 600;
}

.dev-significant {
  color: var(--el-color-danger);
  font-weight: 600;
}

.text-warning {
  color: var(--el-color-warning);
}

.create-head {
  margin-top: 4px;
}

.edit-head {
  margin-bottom: 12px;
}

.edit-main {
  margin-top: 4px;
}

.edit-actions {
  margin-bottom: 10px;
  display: flex;
  gap: 8px;
}

.detail-head {
  position: relative;
}

.detail-actions {
  margin: 10px 0 0;
  display: flex;
  justify-content: flex-end;
}

.history-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.history-meta {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.history-opinion {
  flex-basis: 100%;
  margin: 4px 0 0;
  color: var(--el-text-color-regular);
}

.change-before {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.change-arrow {
  margin: 0 6px;
  vertical-align: middle;
}

.change-after {
  color: var(--el-color-danger);
  font-weight: 600;
}

.form-hint {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-filter {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
